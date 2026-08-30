package ai.qorva.core.scheduler;

import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import ai.qorva.core.mapper.OpenAIResultMapper;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.CandidateUpdateEmailService;
import ai.qorva.core.service.CandidateUpdateService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.LibraryQualityCacheEvictor;
import ai.qorva.core.service.OpenAIService;
import ai.qorva.core.service.S3StorageService;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drains background jobs. Multi-instance safe: jobs are claimed with an atomic
 * findAndModify lease that is heartbeated per batch; leases from crashed instances
 * expire and get reclaimed. LLM concurrency is intentionally small per job so
 * background work never starves interactive extraction traffic.
 */
@Slf4j
@Component
public class BackgroundJobWorker {

	private static final String INSTANCE_ID = UUID.randomUUID().toString();
	private static final Duration LEASE = Duration.ofMinutes(2);
	private static final int BATCH_SIZE = 10;
	private static final int LLM_CONCURRENCY = 3;
	private static final int ERROR_SAMPLE_CAP = 20;
	/** Bulk imports: cancellation/quota re-checked every N submissions, progress written every N completions. */
	private static final int BULK_CONTROL_CHECK_EVERY = 20;
	private static final int BULK_HEARTBEAT_EVERY = 10;

	/**
	 * Bulk imports get their own, larger LLM budget: unlike re-analyze (which shares the
	 * instance with interactive uploads by design), a bulk import is the tenant's primary
	 * activity while it runs. Still far below the HTTP pool's 50-per-route ceiling.
	 */
	@org.springframework.beans.factory.annotation.Value("${qorva.jobs.bulk-llm-concurrency:10}")
	private int bulkLlmConcurrency = 10;
	private static final long SEND_PACE_MS = 150;   // ≤ ~7 emails/sec toward the provider

	private final MongoTemplate mongoTemplate;
	private final CVRepository cvRepository;
	private final CVService cvService;
	private final OpenAIService openAIService;
	private final OpenAIResultMapper openAIResultMapper;
	private final UsageMonitoringService usageMonitoringService;
	private final LibraryQualityCacheEvictor cacheEvictor;
	private final CandidateUpdateService candidateUpdateService;
	private final CandidateUpdateEmailService candidateUpdateEmailService;
	private final TenantService tenantService;
	private final UserService userService;
	private final S3StorageService s3StorageService;
	private final JobPostService jobPostService;

	public BackgroundJobWorker(
		MongoTemplate mongoTemplate,
		CVRepository cvRepository,
		CVService cvService,
		OpenAIService openAIService,
		OpenAIResultMapper openAIResultMapper,
		UsageMonitoringService usageMonitoringService,
		LibraryQualityCacheEvictor cacheEvictor,
		CandidateUpdateService candidateUpdateService,
		CandidateUpdateEmailService candidateUpdateEmailService,
		TenantService tenantService,
		UserService userService,
		S3StorageService s3StorageService,
		JobPostService jobPostService
	) {
		this.mongoTemplate = mongoTemplate;
		this.cvRepository = cvRepository;
		this.cvService = cvService;
		this.openAIService = openAIService;
		this.openAIResultMapper = openAIResultMapper;
		this.usageMonitoringService = usageMonitoringService;
		this.cacheEvictor = cacheEvictor;
		this.candidateUpdateService = candidateUpdateService;
		this.candidateUpdateEmailService = candidateUpdateEmailService;
		this.tenantService = tenantService;
		this.userService = userService;
		this.s3StorageService = s3StorageService;
		this.jobPostService = jobPostService;
	}

	@Scheduled(fixedDelayString = "${qorva.jobs.poll-delay-ms:5000}")
	public void poll() {
		BackgroundJob job = claimNextJob();
		if (job == null) {
			return;
		}
		log.info("Job {} claimed by {} (type={} issueKey={} tenant={})",
			job.getId(), INSTANCE_ID, job.getType(), job.getIssueKey(), job.getTenantId());
		try {
			if (BackgroundJob.TYPE_REANALYZE.equals(job.getType())) {
				runReanalyze(job);
			} else if (BackgroundJob.TYPE_CANDIDATE_UPDATE_CAMPAIGN.equals(job.getType())) {
				runCampaign(job);
			} else if (BackgroundJob.TYPE_BULK_CV_UPLOAD.equals(job.getType())) {
				runBulkUpload(job);
			} else {
				fail(job, "unsupported_job_type");
			}
		} catch (Exception e) {
			log.error("Job {} crashed", job.getId(), e);
			fail(job, "internal_error");
		}
	}

	/** Atomic claim: PENDING, or RUNNING with an expired lease (crashed worker). */
	private BackgroundJob claimNextJob() {
		var now = Instant.now();
		var query = new Query(new Criteria().orOperator(
			Criteria.where("status").is(BackgroundJob.STATUS_PENDING),
			Criteria.where("status").is(BackgroundJob.STATUS_RUNNING).and("leaseExpiresAt").lt(now)
		)).limit(1);
		var update = new Update()
			.set("status", BackgroundJob.STATUS_RUNNING)
			.set("leaseOwner", INSTANCE_ID)
			.set("leaseExpiresAt", now.plus(LEASE))
			.setOnInsert("startedAt", now);
		var claimed = mongoTemplate.findAndModify(query, update,
			FindAndModifyOptions.options().returnNew(true), BackgroundJob.class);
		if (claimed != null && claimed.getStartedAt() == null) {
			mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(claimed.getId())),
				Update.update("startedAt", now), BackgroundJob.class);
			claimed.setStartedAt(now);
		}
		return claimed;
	}

	private void runReanalyze(BackgroundJob job) {
		var tenantId = job.getTenantId();
		var issueKey = QualityIssueKeyEnum.valueOf(job.getIssueKey());
		// Materialize ids once — criteria re-querying mid-run would loop on items the
		// re-analysis doesn't fix (e.g. OUTDATED). Ids only: bounded and cheap.
		var ids = cvRepository.findQualityIssueCvIds(new ObjectId(tenantId), issueKey);

		var processed = new AtomicLong(job.getProcessed());
		var succeeded = new AtomicLong(job.getSucceeded());
		var failed = new AtomicLong(job.getFailed());
		var skipped = new AtomicLong(job.getSkipped());
		var errorSamples = new ArrayList<String>(job.getErrorSamples() != null ? job.getErrorSamples() : List.of());
		var llmPermits = new Semaphore(LLM_CONCURRENCY);

		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
				if (isCancelled(job.getId())) {
					log.info("Job {} cancelled — stopping after {} items", job.getId(), processed.get());
					return;
				}
				if (usageMonitoringService.hasExceededLimit(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS)) {
					fail(job, "quota_exceeded");
					return;
				}

				var batch = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
				var futures = batch.stream()
					.map(id -> CompletableFuture.runAsync(() -> {
						try {
							llmPermits.acquire();
							try {
								reanalyzeOne(id.toHexString(), tenantId, skipped, succeeded);
							} finally {
								llmPermits.release();
							}
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							failed.incrementAndGet();
						} catch (Exception e) {
							failed.incrementAndGet();
							if (errorSamples.size() < ERROR_SAMPLE_CAP) {
								errorSamples.add(id.toHexString() + ": " + e.getMessage());
							}
							log.warn("Job {} — re-analysis failed for CV {}", job.getId(), id, e);
						} finally {
							processed.incrementAndGet();
						}
					}, executor))
					.toList();
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				heartbeat(job.getId(), processed.get(), succeeded.get(), failed.get(), skipped.get(), errorSamples);
			}
		}

		var finalStatus = failed.get() > 0
			? BackgroundJob.STATUS_COMPLETED_WITH_ERRORS
			: BackgroundJob.STATUS_COMPLETED;
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(job.getId())),
			new Update()
				.set("status", finalStatus)
				.set("processed", processed.get())
				.set("succeeded", succeeded.get())
				.set("failed", failed.get())
				.set("skipped", skipped.get())
				.set("errorSamples", errorSamples)
				.set("finishedAt", Instant.now()),
			BackgroundJob.class);
		cacheEvictor.evict(tenantId);
		log.info("Job {} finished: {} — {}/{} succeeded, {} failed, {} skipped",
			job.getId(), finalStatus, succeeded.get(), processed.get(), failed.get(), skipped.get());
	}

	/**
	 * Bulk CV import: drains the job's staged S3 files through the same pipeline as live
	 * uploads (parse → LLM extraction → S3 attachment → persist). When the tenant's
	 * screening-action quota runs out mid-job, the remainder is skipped — never silently
	 * billed — and the job finishes COMPLETED_WITH_ERRORS with reason quota_exceeded.
	 */
	private void runBulkUpload(BackgroundJob job) {
		var tenantId = job.getTenantId();
		var files = job.getStagedFiles() != null ? job.getStagedFiles() : List.<BackgroundJob.StagedFile>of();

		var processed = new AtomicLong(job.getProcessed());
		var succeeded = new AtomicLong(job.getSucceeded());
		var failed = new AtomicLong(job.getFailed());
		var skipped = new AtomicLong(job.getSkipped());
		var errorSamples = Collections.synchronizedList(
			new ArrayList<String>(job.getErrorSamples() != null ? job.getErrorSamples() : List.of()));
		var llmPermits = new Semaphore(bulkLlmConcurrency);
		boolean quotaExhausted = false;

		// Resume support: a reclaimed lease continues after the already-processed prefix.
		int startFrom = (int) Math.min(processed.get(), files.size());
		int submitted = startFrom;

		// Sliding-window pipeline: the submitting thread blocks on a permit, so at most
		// bulkLlmConcurrency files are in flight and a slow file never idles the others
		// (the old per-10 join barrier did). Control checks are throttled on submission;
		// progress heartbeats ride on completions.
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = new ArrayList<CompletableFuture<Void>>(files.size() - startFrom);
			for (int i = startFrom; i < files.size(); i++) {
				if ((i - startFrom) % BULK_CONTROL_CHECK_EVERY == 0) {
					if (isCancelled(job.getId())) {
						log.info("Job {} cancelled — stopping after {} files", job.getId(), processed.get());
						CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
						return;
					}
					if (usageMonitoringService.hasExceededLimit(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS)) {
						quotaExhausted = true;
						break;
					}
				}
				try {
					llmPermits.acquire();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
				final var staged = files.get(i);
				futures.add(CompletableFuture.runAsync(() -> {
					try {
						try {
							var bytes = s3StorageService.fetchObjectBytes(staged.getS3Key());
							cvService.processFile(bytes, staged.getFilename(), staged.getContentType(), tenantId);
							succeeded.incrementAndGet();
						} finally {
							llmPermits.release();
						}
						deleteStagedQuietly(staged);
					} catch (Exception e) {
						failed.incrementAndGet();
						if (errorSamples.size() < ERROR_SAMPLE_CAP) {
							errorSamples.add(staged.getFilename() + ": " + e.getMessage());
						}
						deleteStagedQuietly(staged);
						log.warn("Job {} — bulk import failed for {}", job.getId(), staged.getFilename(), e);
					} finally {
						long done = processed.incrementAndGet();
						if (done % BULK_HEARTBEAT_EVERY == 0) {
							heartbeat(job.getId(), done, succeeded.get(), failed.get(), skipped.get(),
								List.copyOf(errorSamples));
						}
					}
				}, executor));
				submitted = i + 1;
			}
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		}

		if (quotaExhausted) {
			long remainder = files.size() - submitted;
			skipped.addAndGet(remainder);
			processed.addAndGet(remainder);
			files.subList(submitted, files.size()).forEach(this::deleteStagedQuietly);
		}

		var finalStatus = (failed.get() > 0 || quotaExhausted)
			? BackgroundJob.STATUS_COMPLETED_WITH_ERRORS
			: BackgroundJob.STATUS_COMPLETED;
		var update = new Update()
			.set("status", finalStatus)
			.set("processed", processed.get())
			.set("succeeded", succeeded.get())
			.set("failed", failed.get())
			.set("skipped", skipped.get())
			.set("errorSamples", List.copyOf(errorSamples))
			.set("finishedAt", Instant.now());
		if (quotaExhausted) {
			update.set("failureReason", "quota_exceeded");
		}
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(job.getId())), update, BackgroundJob.class);

		if (succeeded.get() > 0) {
			try {
				jobPostService.markOpenJobPostsAsNeedingReports(tenantId);
			} catch (Exception e) {
				log.warn("Job {} — could not mark job posts as needing reports", job.getId(), e);
			}
			cacheEvictor.evict(tenantId);
		}
		log.info("Job {} finished: {} — {}/{} imported, {} failed, {} skipped{}",
			job.getId(), finalStatus, succeeded.get(), processed.get(), failed.get(), skipped.get(),
			quotaExhausted ? " (quota exhausted)" : "");
	}

	private void deleteStagedQuietly(BackgroundJob.StagedFile staged) {
		try {
			s3StorageService.deleteObject(staged.getS3Key());
		} catch (Exception e) {
			log.warn("Could not delete staged object {}: {}", staged.getS3Key(), e.getMessage());
		}
	}

	/** Re-runs LLM extraction from stored rawText; the normal update path preserves tags/attachment/VERIFIED. */
	private void reanalyzeOne(String cvId, String tenantId, AtomicLong skipped, AtomicLong succeeded) throws Exception {
		var existing = cvService.findOneById(cvId);
		if (!StringUtils.hasText(existing.getRawText())) {
			// No text layer (scanned/designed CV) — these used to be skipped forever.
			// Vision re-analysis reads the original document from S3 instead.
			if (cvService.reanalyzeFromOriginal(existing, tenantId)) {
				succeeded.incrementAndGet();
			} else {
				skipped.incrementAndGet();
			}
			return;
		}

		var outputConverter = new BeanOutputConverter<>(CVOutputDTO.class);
		var content = openAIService.streamCVExtraction(existing.getRawText());
		if (!StringUtils.hasText(content)) {
			throw new IllegalStateException("empty extraction result");
		}
		var updated = openAIResultMapper.map(outputConverter.convert(content));
		updated.setTenantId(tenantId);

		// updateOne merges the existing document into null fields (tags, attachment, rawText,
		// applicantNumber, VERIFIED contentDate) and recomputes flags + contentDate.
		cvService.updateOne(cvId, updated);
		usageMonitoringService.incrementUsage(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, 1);
		succeeded.incrementAndGet();
	}

	/**
	 * Candidate-update campaign: one invitation email per eligible CV, paced to respect
	 * provider send limits. Eligibility (has email, not suppressed, no active request)
	 * is checked per item at send time — the estimate is an upper bound.
	 */
	private void runCampaign(BackgroundJob job) throws Exception {
		var tenantId = job.getTenantId();
		var issueKey = QualityIssueKeyEnum.valueOf(job.getIssueKey());
		var ids = cvRepository.findQualityIssueCvIds(new ObjectId(tenantId), issueKey);
		var tenantName = tenantService.findOneById(tenantId).getTenantName();

		// Recruiter-authored copy snapshotted at submit time; null → built-in localized copy.
		var customTemplate = StringUtils.hasText(job.getEmailSubject()) && StringUtils.hasText(job.getEmailBody())
			? new CandidateUpdateEmailService.CustomTemplate(job.getEmailSubject(), job.getEmailBody())
			: null;
		var senderName = resolveSenderName(job.getCreatedBy());

		long processed = job.getProcessed(), succeeded = job.getSucceeded(),
			failed = job.getFailed(), skipped = job.getSkipped();
		var errorSamples = new ArrayList<String>(job.getErrorSamples() != null ? job.getErrorSamples() : List.of());

		for (var id : ids) {
			if (processed % 25 == 0) {
				if (isCancelled(job.getId())) {
					log.info("Job {} cancelled — stopping after {} sends", job.getId(), processed);
					return;
				}
				heartbeat(job.getId(), processed, succeeded, failed, skipped, errorSamples);
			}
			processed++;
			try {
				var cv = cvService.findOneById(id.toHexString());
				var contact = cv.getPersonalInformation() != null ? cv.getPersonalInformation().getContact() : null;
				var email = contact != null ? contact.getEmail() : null;
				if (!StringUtils.hasText(email)
					|| candidateUpdateService.isSuppressed(tenantId, email)
					|| candidateUpdateService.hasActiveRequest(tenantId, cv.getId())) {
					skipped++;
					continue;
				}
				var token = candidateUpdateService.createRequest(tenantId, cv.getId(), email, job.getLanguage());
				candidateUpdateEmailService.sendUpdateInvitation(
					email,
					cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null,
					tenantName,
					candidateUpdateService.buildUpdateLink(token),
					job.getLanguage(),
					customTemplate,
					senderName);
				succeeded++;
				Thread.sleep(SEND_PACE_MS);   // provider-friendly pacing
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw e;
			} catch (Exception e) {
				failed++;
				if (errorSamples.size() < ERROR_SAMPLE_CAP) {
					errorSamples.add(id.toHexString() + ": " + e.getMessage());
				}
				log.warn("Job {} — invitation failed for CV {}", job.getId(), id, e);
			}
		}

		var finalStatus = failed > 0 ? BackgroundJob.STATUS_COMPLETED_WITH_ERRORS : BackgroundJob.STATUS_COMPLETED;
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(job.getId())),
			new Update()
				.set("status", finalStatus)
				.set("processed", processed)
				.set("succeeded", succeeded)
				.set("failed", failed)
				.set("skipped", skipped)
				.set("errorSamples", errorSamples)
				.set("finishedAt", Instant.now()),
			BackgroundJob.class);
		log.info("Job {} finished: {} — {} invitations sent, {} failed, {} skipped",
			job.getId(), finalStatus, succeeded, failed, skipped);
	}

	/** Full name of the recruiter who launched the campaign — signs the invitation emails. */
	private String resolveSenderName(String creatorEmail) {
		if (!StringUtils.hasText(creatorEmail)) {
			return null;
		}
		try {
			var creator = userService.findByEmail(creatorEmail);
			if (creator == null) {
				return null;
			}
			var fullName = ((creator.getFirstName() != null ? creator.getFirstName() : "") + " "
				+ (creator.getLastName() != null ? creator.getLastName() : "")).trim();
			return fullName.isEmpty() ? null : fullName;
		} catch (Exception e) {
			log.warn("Could not resolve campaign sender name for {}: {}", creatorEmail, e.getMessage());
			return null;
		}
	}

	private boolean isCancelled(String jobId) {
		var current = mongoTemplate.findById(jobId, BackgroundJob.class);
		return current == null || BackgroundJob.STATUS_CANCELLED.equals(current.getStatus());
	}

	private void heartbeat(String jobId, long processed, long succeeded, long failed, long skipped, List<String> errorSamples) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(jobId)),
			new Update()
				.set("processed", processed)
				.set("succeeded", succeeded)
				.set("failed", failed)
				.set("skipped", skipped)
				.set("errorSamples", errorSamples)
				.set("leaseExpiresAt", Instant.now().plus(LEASE)),
			BackgroundJob.class);
	}

	private void fail(BackgroundJob job, String reason) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(job.getId())),
			new Update()
				.set("status", BackgroundJob.STATUS_FAILED)
				.set("failureReason", reason)
				.set("finishedAt", Instant.now()),
			BackgroundJob.class);
		cacheEvictor.evict(job.getTenantId());
	}
}
