package ai.qorva.core.service;

import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.repository.BackgroundJobRepository;
import ai.qorva.core.dto.BackgroundJobData;
import ai.qorva.core.dto.BulkCvUploadData;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lifecycle of asynchronous bulk CV imports: a DRAFT job collects staged files in
 * chunks (each chunk is a fast S3 write — no parsing, no LLM), then start() hands it
 * to the BackgroundJobWorker. The max files per job is a plan feature
 * (limits.bulkUploadFiles: Starter 100 / Pro 300 / Scale 1000).
 */
@Slf4j
@Service
public class BulkCvUploadService {

	/** Fallback when the tenant's plan cannot be resolved — the smallest tier's cap. */
	public static final int DEFAULT_MAX_FILES = 100;

	/** Staging chunks stay at or below the sync-upload cap so Tomcat's part limit never bites. */
	public static final int MAX_FILES_PER_CHUNK = CVService.SYNC_UPLOAD_MAX_FILES;

	private static final List<String> ACTIVE_STATUSES =
		List.of(BackgroundJob.STATUS_DRAFT, BackgroundJob.STATUS_PENDING, BackgroundJob.STATUS_RUNNING);

	private final BackgroundJobRepository jobRepository;
	private final S3StorageService s3StorageService;
	private final UsageMonitoringService usageMonitoringService;
	private final TenantService tenantService;
	private final ProductReferenceService productReferenceService;
	private final MongoTemplate mongoTemplate;

	@Autowired
	public BulkCvUploadService(
		BackgroundJobRepository jobRepository,
		S3StorageService s3StorageService,
		UsageMonitoringService usageMonitoringService,
		TenantService tenantService,
		ProductReferenceService productReferenceService,
		MongoTemplate mongoTemplate
	) {
		this.jobRepository = jobRepository;
		this.s3StorageService = s3StorageService;
		this.usageMonitoringService = usageMonitoringService;
		this.tenantService = tenantService;
		this.productReferenceService = productReferenceService;
		this.mongoTemplate = mongoTemplate;
	}

	/** Max files per bulk import for this tenant's plan (same resolution as the email-template cap). */
	public int maxFilesForTenant(String tenantId) {
		try {
			var sub = tenantService.findOneById(tenantId).getSubscriptionInfo();
			if (sub != null && StringUtils.hasText(sub.getPriceId())) {
				var product = productReferenceService.findByStripePriceId(sub.getPriceId());
				if (product != null && product.getFeatures() != null && product.getFeatures().getLimits() != null
					&& product.getFeatures().getLimits().getBulkUploadFiles() != null) {
					return product.getFeatures().getLimits().getBulkUploadFiles();
				}
			}
		} catch (Exception e) {
			log.warn("Could not resolve bulk-upload limit for tenant {}: {}", tenantId, e.getMessage());
		}
		return DEFAULT_MAX_FILES;
	}

	public BulkCvUploadData.CreateResponse create(String tenantId, String createdBy) throws QorvaException {
		if (jobRepository.existsByTenantIdAndTypeAndStatusIn(tenantId, BackgroundJob.TYPE_BULK_CV_UPLOAD, ACTIVE_STATUSES)) {
			throw new QorvaException(QorvaErrorCodes.BULK_JOB_ACTIVE_EXISTS, HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}
		var job = jobRepository.save(BackgroundJob.builder()
			.tenantId(tenantId)
			.type(BackgroundJob.TYPE_BULK_CV_UPLOAD)
			.status(BackgroundJob.STATUS_DRAFT)
			.stagedFiles(List.of())
			.errorSamples(List.of())
			.createdBy(createdBy)
			.createdAt(Instant.now())
			.build());
		var maxFiles = maxFilesForTenant(tenantId);
		log.info("Bulk upload job {} created for tenant {} (cap {})", job.getId(), tenantId, maxFiles);
		return new BulkCvUploadData.CreateResponse(job.getId(), maxFiles);
	}

	public BulkCvUploadData.StageResponse appendFiles(String tenantId, String jobId, List<MultipartFile> files) throws QorvaException {
		findOwnedDraft(tenantId, jobId);
		if (files == null || files.isEmpty()) {
			throw new QorvaException(QorvaErrorCodes.BULK_JOB_NO_FILES, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		int maxFiles = maxFilesForTenant(tenantId);

		// Stage to S3 first under collision-free keys, then append atomically — the client
		// uploads chunks in parallel, so two requests must never lose each other's files.
		var items = new ArrayList<BackgroundJob.StagedFile>();
		for (var file : files) {
			if (file.isEmpty()) {
				continue;
			}
			var key = s3StorageService.uploadStagedCv(tenantId, jobId, UUID.randomUUID().toString(), file);
			items.add(BackgroundJob.StagedFile.builder()
				.s3Key(key)
				.filename(file.getOriginalFilename())
				.contentType(file.getContentType())
				.build());
		}
		if (items.isEmpty()) {
			throw new QorvaException(QorvaErrorCodes.BULK_JOB_NO_FILES, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}

		// Atomic push with a size guard: element [maxFiles - n] existing would mean the
		// append exceeds the plan cap, so the update matches nothing and we roll back.
		int guardIndex = Math.max(0, maxFiles - items.size());
		var query = Query.query(Criteria.where("_id").is(jobId)
			.and("status").is(BackgroundJob.STATUS_DRAFT)
			.and("stagedFiles." + guardIndex).exists(false));
		var update = new Update()
			.push("stagedFiles").each(items.toArray())
			.inc("total", items.size());
		var result = mongoTemplate.updateFirst(query, update, BackgroundJob.class);

		if (result.getModifiedCount() == 0) {
			items.forEach(item -> {
				try {
					s3StorageService.deleteObject(item.getS3Key());
				} catch (Exception e) {
					log.warn("Could not roll back staged object {}: {}", item.getS3Key(), e.getMessage());
				}
			});
			// The job was a DRAFT moments ago, so a miss means either a concurrent start
			// or the plan cap — re-read to report the right error.
			var current = findOwned(tenantId, jobId);
			if (!BackgroundJob.STATUS_DRAFT.equals(current.getStatus())) {
				throw new QorvaException(QorvaErrorCodes.BULK_JOB_NOT_DRAFT, HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
			}
			throw new QorvaException(QorvaErrorCodes.BULK_LIMIT_FOR_PLAN,
				HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN, maxFiles);
		}

		var refreshed = findOwned(tenantId, jobId);
		int stagedCount = refreshed.getStagedFiles() != null ? refreshed.getStagedFiles().size() : 0;
		return new BulkCvUploadData.StageResponse(jobId, stagedCount, maxFiles);
	}

	public BulkCvUploadData.StartResponse start(String tenantId, String jobId) throws QorvaException {
		var job = findOwnedDraft(tenantId, jobId);
		var staged = job.getStagedFiles();
		if (staged == null || staged.isEmpty()) {
			throw new QorvaException(QorvaErrorCodes.BULK_JOB_NO_FILES, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}

		// The worker gates per file and skips the remainder when quota runs out; refusing
		// here only when nothing at all can be processed keeps partial imports possible.
		if (!usageMonitoringService.hasCapacityFor(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, 1)) {
			throw new QorvaException(QorvaErrorCodes.USAGE_SCREENING_LIMIT_EXCEEDED,
				HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN);
		}

		job.setStatus(BackgroundJob.STATUS_PENDING);
		job.setTotal(staged.size());
		jobRepository.save(job);

		long willProcess = staged.size();
		Integer remaining = remainingScreeningQuota(tenantId);
		if (remaining != null) {
			willProcess = Math.min(willProcess, remaining);
		}
		log.info("Bulk upload job {} started for tenant {}: {} files ({} within quota)",
			jobId, tenantId, staged.size(), willProcess);
		return new BulkCvUploadData.StartResponse(BackgroundJobData.JobView.from(job), willProcess);
	}

	public BackgroundJobData.JobView get(String tenantId, String jobId) throws QorvaException {
		var job = jobRepository.findByIdAndTenantId(jobId, tenantId)
			.orElseThrow(() -> new QorvaException(QorvaErrorCodes.BULK_JOB_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
		return BackgroundJobData.JobView.from(job);
	}

	public BackgroundJobData.JobList list(String tenantId) {
		var jobs = jobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 10)).stream()
			.filter(job -> BackgroundJob.TYPE_BULK_CV_UPLOAD.equals(job.getType()))
			.map(BackgroundJobData.JobView::from)
			.toList();
		return new BackgroundJobData.JobList(jobs);
	}

	public BackgroundJobData.JobView cancel(String tenantId, String jobId) throws QorvaException {
		var job = jobRepository.findByIdAndTenantId(jobId, tenantId)
			.orElseThrow(() -> new QorvaException(QorvaErrorCodes.BULK_JOB_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
		if (ACTIVE_STATUSES.contains(job.getStatus())) {
			job.setStatus(BackgroundJob.STATUS_CANCELLED);
			job.setFinishedAt(Instant.now());
			jobRepository.save(job);
			deleteStagedObjects(job);
			log.info("Bulk upload job {} cancelled by tenant {}", jobId, tenantId);
		}
		return BackgroundJobData.JobView.from(job);
	}

	/** Best-effort: deleteObject is idempotent, so files the worker already removed are no-ops. */
	private void deleteStagedObjects(BackgroundJob job) {
		if (job.getStagedFiles() == null) {
			return;
		}
		for (var staged : job.getStagedFiles()) {
			try {
				s3StorageService.deleteObject(staged.getS3Key());
			} catch (Exception e) {
				log.warn("Could not delete staged object {}: {}", staged.getS3Key(), e.getMessage());
			}
		}
	}

	private BackgroundJob findOwned(String tenantId, String jobId) throws QorvaException {
		return jobRepository.findByIdAndTenantId(jobId, tenantId)
			.orElseThrow(() -> new QorvaException(QorvaErrorCodes.BULK_JOB_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
	}

	private BackgroundJob findOwnedDraft(String tenantId, String jobId) throws QorvaException {
		var job = findOwned(tenantId, jobId);
		if (!BackgroundJob.STATUS_DRAFT.equals(job.getStatus())) {
			throw new QorvaException(QorvaErrorCodes.BULK_JOB_NOT_DRAFT, HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}
		return job;
	}

	private Integer remainingScreeningQuota(String tenantId) {
		return usageMonitoringService.findCurrentPeriodByTenantId(tenantId)
			.map(usage -> {
				var metrics = usage.getFeatures() != null ? usage.getFeatures().getScreeningActions() : null;
				if (metrics == null || metrics.getLimit() == null) return null;
				return Math.max(0, metrics.getLimit() - (metrics.getConsumed() != null ? metrics.getConsumed() : 0));
			})
			.orElse(null);
	}
}
