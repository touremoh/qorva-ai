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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

	@Autowired
	public BulkCvUploadService(
		BackgroundJobRepository jobRepository,
		S3StorageService s3StorageService,
		UsageMonitoringService usageMonitoringService,
		TenantService tenantService,
		ProductReferenceService productReferenceService
	) {
		this.jobRepository = jobRepository;
		this.s3StorageService = s3StorageService;
		this.usageMonitoringService = usageMonitoringService;
		this.tenantService = tenantService;
		this.productReferenceService = productReferenceService;
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
		var job = findOwnedDraft(tenantId, jobId);
		if (files == null || files.isEmpty()) {
			throw new QorvaException(QorvaErrorCodes.BULK_JOB_NO_FILES, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}

		var staged = job.getStagedFiles() != null ? new ArrayList<>(job.getStagedFiles()) : new ArrayList<BackgroundJob.StagedFile>();
		int maxFiles = maxFilesForTenant(tenantId);
		if (staged.size() + files.size() > maxFiles) {
			throw new QorvaException(QorvaErrorCodes.BULK_LIMIT_FOR_PLAN,
				HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN, maxFiles);
		}

		for (var file : files) {
			if (file.isEmpty()) {
				continue;
			}
			var key = s3StorageService.uploadStagedCv(tenantId, jobId, staged.size(), file);
			staged.add(BackgroundJob.StagedFile.builder()
				.s3Key(key)
				.filename(file.getOriginalFilename())
				.contentType(file.getContentType())
				.build());
		}

		job.setStagedFiles(staged);
		job.setTotal(staged.size());
		jobRepository.save(job);
		return new BulkCvUploadData.StageResponse(jobId, staged.size(), maxFiles);
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

	private BackgroundJob findOwnedDraft(String tenantId, String jobId) throws QorvaException {
		var job = jobRepository.findByIdAndTenantId(jobId, tenantId)
			.orElseThrow(() -> new QorvaException(QorvaErrorCodes.BULK_JOB_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
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
