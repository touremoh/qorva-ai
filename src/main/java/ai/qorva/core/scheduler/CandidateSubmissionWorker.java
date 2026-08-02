package ai.qorva.core.scheduler;

import ai.qorva.core.dao.entity.CandidateUpdateRequest;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.CandidateUpdateService;
import ai.qorva.core.service.S3StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Drains staged candidate submissions (async resume uploads from the public form).
 * Separate from {@link BackgroundJobWorker} on purpose: submissions are many small
 * independent jobs — several candidates of the same tenant must process in parallel,
 * not serialize behind a one-job-per-tenant rule. Multi-instance safe via the same
 * atomic findAndModify lease pattern; stale PROCESSING claims from crashed instances
 * are reclaimed. LLM concurrency is bounded so submissions never starve interactive
 * extraction traffic.
 */
@Slf4j
@Component
public class CandidateSubmissionWorker {

	private static final String INSTANCE_ID = UUID.randomUUID().toString();
	private static final Duration LEASE = Duration.ofMinutes(3);
	private static final int CLAIM_BATCH = 5;
	private static final int LLM_CONCURRENCY = 3;

	private final MongoTemplate mongoTemplate;
	private final CVService cvService;
	private final CandidateUpdateService candidateUpdateService;
	private final S3StorageService s3StorageService;

	public CandidateSubmissionWorker(
		MongoTemplate mongoTemplate,
		CVService cvService,
		CandidateUpdateService candidateUpdateService,
		S3StorageService s3StorageService
	) {
		this.mongoTemplate = mongoTemplate;
		this.cvService = cvService;
		this.candidateUpdateService = candidateUpdateService;
		this.s3StorageService = s3StorageService;
	}

	@Scheduled(fixedDelayString = "${qorva.candidate-submissions.poll-delay-ms:2000}")
	public void poll() {
		var claimed = claimBatch();
		if (claimed.isEmpty()) {
			return;
		}
		var llmPermits = new Semaphore(LLM_CONCURRENCY);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = claimed.stream()
				.map(request -> CompletableFuture.runAsync(() -> {
					try {
						llmPermits.acquire();
						try {
							processOne(request);
						} finally {
							llmPermits.release();
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						markFailed(request, "interrupted");
					} catch (Exception e) {
						log.warn("Candidate submission {} failed", request.getId(), e);
						markFailed(request, "processing_failed");
					}
				}, executor))
				.toList();
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		}
	}

	/** Atomic claims: SUBMITTED, or PROCESSING with an expired lease (crashed worker). */
	private List<CandidateUpdateRequest> claimBatch() {
		var claimed = new ArrayList<CandidateUpdateRequest>(CLAIM_BATCH);
		for (int i = 0; i < CLAIM_BATCH; i++) {
			var now = Instant.now();
			var query = new Query(new Criteria().orOperator(
				Criteria.where("status").is(CandidateUpdateRequest.STATUS_SUBMITTED),
				Criteria.where("status").is(CandidateUpdateRequest.STATUS_PROCESSING).and("leaseExpiresAt").lt(now)
			)).limit(1);
			var update = new Update()
				.set("status", CandidateUpdateRequest.STATUS_PROCESSING)
				.set("processingStage", CandidateUpdateRequest.STAGE_PARSING)
				.set("leaseOwner", INSTANCE_ID)
				.set("leaseExpiresAt", now.plus(LEASE));
			var request = mongoTemplate.findAndModify(query, update,
				FindAndModifyOptions.options().returnNew(true), CandidateUpdateRequest.class);
			if (request == null) {
				break;
			}
			claimed.add(request);
		}
		return claimed;
	}

	private void processOne(CandidateUpdateRequest request) throws Exception {
		log.info("Processing candidate submission {} (tenant {}) on {}",
			request.getId(), request.getTenantId(), INSTANCE_ID);

		var bytes = s3StorageService.fetchObjectBytes(request.getPendingFileKey());

		// PARSING (set at claim time) — the LLM extraction dominates the runtime.
		var created = cvService.processFile(bytes, request.getOriginalFileName(), null, request.getTenantId());

		advanceStage(request.getId(), CandidateUpdateRequest.STAGE_UPDATING);
		candidateUpdateService.finalizeStagedSubmission(request, created.getId());

		s3StorageService.deleteObject(request.getPendingFileKey());
		log.info("Candidate submission {} completed", request.getId());
	}

	/** Also renews the lease — a slow LLM call must not get reclaimed mid-flight. */
	private void advanceStage(String requestId, String stage) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(requestId)),
			new Update()
				.set("processingStage", stage)
				.set("leaseExpiresAt", Instant.now().plus(LEASE)),
			CandidateUpdateRequest.class);
	}

	/** SUBMIT_FAILED is retryable: the token stays valid and the candidate may resubmit. */
	private void markFailed(CandidateUpdateRequest request, String reason) {
		try {
			mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(request.getId())),
				new Update()
					.set("status", CandidateUpdateRequest.STATUS_SUBMIT_FAILED)
					.set("processingError", reason)
					.set("processingStage", null)
					.set("pendingFileKey", null),
				CandidateUpdateRequest.class);
			s3StorageService.deleteObject(request.getPendingFileKey());
		} catch (Exception e) {
			log.error("Failed to mark candidate submission {} as failed", request.getId(), e);
		}
	}
}
