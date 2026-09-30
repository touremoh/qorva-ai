package ai.qorva.core.service;

import ai.qorva.core.security.TenantScope;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ats.AtsWriteBackService;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class AIScreeningService {

	private static final int MAX_CONCURRENT_OPENAI_CALLS = 40;
	private final Semaphore openAiCallLimiter = new Semaphore(MAX_CONCURRENT_OPENAI_CALLS);

	private final CVService cvService;
	private final OpenAIService openAIService;
	private final MatchingReportService matchingReportService;
	private final JobPostService jobPostService;
	private final UsageMonitoringService usageMonitoringService;
	private final AtsWriteBackService atsWriteBackService;

	@Autowired
	public AIScreeningService(CVService cvService, OpenAIService openAIService, MatchingReportService matchingReportService, JobPostService jobPostService, UsageMonitoringService usageMonitoringService, AtsWriteBackService atsWriteBackService) {
		this.cvService = cvService;
		this.openAIService = openAIService;
		this.matchingReportService = matchingReportService;
		this.jobPostService = jobPostService;
		this.usageMonitoringService = usageMonitoringService;
		this.atsWriteBackService = atsWriteBackService;
	}

	public void startScreeningProcess(String tenantId, String languageCode) throws QorvaException {
		log.info("Starting screening process for tenant={}", tenantId);

		var jobPosts = jobPostService.findJobPostsNeedingReports(tenantId);

		if (jobPosts.isEmpty()) {
			log.info("No job posts require matching reports for tenant={}", tenantId);
			return;
		}

		// Virtual threads: each job post (and each candidate within it) runs as a
		// separate virtual thread, yielding on OpenAI I/O. All job posts run concurrently.
		try (var executor = TenantScope.propagating(Executors.newVirtualThreadPerTaskExecutor())) {
			var jobFutures = jobPosts.stream()
				.map(jp -> CompletableFuture.runAsync(() -> processJobPost(jp, tenantId, languageCode, executor), executor))
				.toArray(CompletableFuture[]::new);
			CompletableFuture.allOf(jobFutures).join();
		}

		log.info("Screening process completed for tenant={}", tenantId);
	}

	/**
	 * Matching for the chosen open jobs only (Copilot's start_screening), with the same per-job work as the
	 * tenant-wide run. Checks the plan itself — this path has no controller guard. Returns the jobs screened;
	 * closed or unknown jobs are skipped (ids resolve in the current tenant only).
	 */
	public List<JobPostDTO> screenJobs(String tenantId, List<String> jobIds, String languageCode) throws QorvaException {
		var jobs = new ArrayList<JobPostDTO>();
		for (var id : jobIds) {
			var job = jobPostService.findOneById(id);
			if (JobPostStatusEnum.OPEN.getStatus().equals(job.getStatus())) {
				jobs.add(job);
			}
		}
		if (jobs.isEmpty()) {
			return List.of();
		}
		int estimate = jobs.size() * CVService.DEFAULT_MATCH_LIMIT;
		if (!usageMonitoringService.hasCapacityFor(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, estimate)) {
			throw QorvaErrors.forbidden(QorvaErrorCodes.USAGE_SCREENING_LIMIT_EXCEEDED);
		}
		log.info("Screening {} chosen job(s) for tenant={}", jobs.size(), tenantId);
		try (var executor = TenantScope.propagating(Executors.newVirtualThreadPerTaskExecutor())) {
			var jobFutures = jobs.stream()
				.map(jp -> CompletableFuture.runAsync(() -> processJobPost(jp, tenantId, languageCode, executor), executor))
				.toArray(CompletableFuture[]::new);
			CompletableFuture.allOf(jobFutures).join();
		}
		return List.copyOf(jobs);
	}

	private void processJobPost(JobPostDTO jobPost, String tenantId, String languageCode, Executor executor) {
		if (jobPost.getEmbedding() == null || jobPost.getEmbedding().length == 0) {
			log.warn("Skipping job post {} - embedding not yet available", jobPost.getId());
			return;
		}

		try {
			var matchingCVs = this.cvService.match(jobPost);

			if (!matchingCVs.isEmpty()) {
				var candidateFutures = matchingCVs.stream()
					.map(cv -> CompletableFuture.runAsync(() -> {
						try {
							processCandidate(cv, jobPost, tenantId, languageCode);
						} catch (Exception e) {
							log.error("Error processing candidate {} for job post {}", cv.getId(), jobPost.getId(), e);
						}
					}, executor))
					.toArray(CompletableFuture[]::new);
				CompletableFuture.allOf(candidateFutures).join();
			}

			jobPostService.clearMatchingReportsNeeded(jobPost.getId(), tenantId);
			log.debug("Screening done for job post {} ({} candidates)", jobPost.getId(), matchingCVs.size());
		} catch (QorvaException e) {
			log.error("Error processing job post {}", jobPost.getId(), e);
		}
	}

	private void processCandidate(CVDTO cv, JobPostDTO jobPost, String tenantId, String languageCode) throws QorvaException {
		var analysisDetails = generateReportWithLimit(cv, jobPost, languageCode);
		incrementUsageSilently(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS);
		this.matchingReportService.upsertReport(jobPost, analysisDetails, cv);
		this.atsWriteBackService.maybeEnqueue(cv, jobPost, analysisDetails);
	}

	private MatchingReportDetails generateReportWithLimit(CVDTO cv, JobPostDTO jobPost, String languageCode) throws QorvaException {
		try {
			this.openAiCallLimiter.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new QorvaException("Interrupted while waiting to generate report for candidate " + cv.getId(), e);
		}
		try {
			// The matching view, not the whole CV: recruiter tags ("shortlist", "rejected") must not bias the score.
			return this.openAIService.generateReport(
				QorvaUtils.toJSON(CvMatchingView.of(cv)),
				jobPost.toJobTitleAndDescription(),
				languageCode,
				jobPost.getScoringRules()
			);
		} finally {
			this.openAiCallLimiter.release();
		}
	}

	private void incrementUsageSilently(String tenantId, UsageMonitoringService.FeatureKey key) {
		try {
			usageMonitoringService.incrementUsage(tenantId, key, 1);
		} catch (Exception e) {
			log.warn("Failed to increment {} usage for tenant={}", key, tenantId, e);
		}
	}
}
