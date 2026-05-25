package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class AIScreeningService {

	private final CVService cvService;
	private final OpenAIService openAIService;
	private final MatchingReportService matchingReportService;
	private final JobPostService jobPostService;
	private final UsageMonitoringService usageMonitoringService;

	@Autowired
	public AIScreeningService(CVService cvService, OpenAIService openAIService, MatchingReportService matchingReportService, JobPostService jobPostService, UsageMonitoringService usageMonitoringService) {
		this.cvService = cvService;
		this.openAIService = openAIService;
		this.matchingReportService = matchingReportService;
		this.jobPostService = jobPostService;
		this.usageMonitoringService = usageMonitoringService;
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
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var jobFutures = jobPosts.stream()
				.map(jp -> CompletableFuture.runAsync(() -> processJobPost(jp, tenantId, languageCode, executor), executor))
				.toArray(CompletableFuture[]::new);
			CompletableFuture.allOf(jobFutures).join();
		}

		log.info("Screening process completed for tenant={}", tenantId);
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

			jobPostService.clearMatchingReportsNeeded(jobPost.getId());
			log.debug("Screening done for job post {} ({} candidates)", jobPost.getId(), matchingCVs.size());
		} catch (QorvaException e) {
			log.error("Error processing job post {}", jobPost.getId(), e);
		}
	}

	private void processCandidate(CVDTO cv, JobPostDTO jobPost, String tenantId, String languageCode) throws QorvaException {
		var analysisDetails = this.openAIService.generateReport(
			QorvaUtils.toJSON(cv),
			jobPost.toJobTitleAndDescription(),
			languageCode,
			jobPost.getScoringRules()
		);
		incrementUsageSilently(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS);
		this.matchingReportService.upsertReport(jobPost, analysisDetails, cv);
	}

	private void incrementUsageSilently(String tenantId, UsageMonitoringService.FeatureKey key) {
		try {
			usageMonitoringService.incrementUsage(tenantId, key, 1);
		} catch (Exception e) {
			log.warn("Failed to increment {} usage for tenant={}", key, tenantId, e);
		}
	}
}
