package ai.qorva.core.service;

import ai.qorva.core.dao.repository.SimilaritySearchRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.enums.MatchingOutdatedReasonEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.MatchingReportService.ReportState;
import ai.qorva.core.service.ats.AtsWriteBackService;
import ai.qorva.core.utils.QorvaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Matching: scores a job's top N candidates and keeps its reports in step with them. One routine,
 * {@link #matchJob}, serves every way of starting it — the asynchronous run the recruiter launches, and
 * Copilot's {@code start_screening} — so the plan's Top N, report reuse and outdated marking cannot drift.
 * <p>
 * A candidate whose report was generated from exactly the same inputs ({@link MatchingFingerprint}) keeps it
 * at no cost; only new or changed pairs are sent to the model and metered. Reports of candidates who left the
 * top N stay, marked outdated, until the recruiter deletes them.
 */
@Slf4j
@Service
public class AIScreeningService {

	private static final int MAX_CONCURRENT_OPENAI_CALLS = 40;
	private final Semaphore openAiCallLimiter = new Semaphore(MAX_CONCURRENT_OPENAI_CALLS);

	/** Most jobs one run may cover (a run is billed up front against the remaining actions). */
	public static final int MAX_JOBS_PER_RUN = 50;

	private final CVService cvService;
	private final OpenAIService openAIService;
	private final MatchingReportService matchingReportService;
	private final JobPostService jobPostService;
	private final UsageMonitoringService usageMonitoringService;
	private final AtsWriteBackService atsWriteBackService;
	private final MatchingTopNPolicy topNPolicy;

	@Autowired
	public AIScreeningService(CVService cvService, OpenAIService openAIService, MatchingReportService matchingReportService,
	                          JobPostService jobPostService, UsageMonitoringService usageMonitoringService,
	                          AtsWriteBackService atsWriteBackService, MatchingTopNPolicy topNPolicy) {
		this.cvService = cvService;
		this.openAIService = openAIService;
		this.matchingReportService = matchingReportService;
		this.jobPostService = jobPostService;
		this.usageMonitoringService = usageMonitoringService;
		this.atsWriteBackService = atsWriteBackService;
		this.topNPolicy = topNPolicy;
	}

	/** Called after each candidate of a run, so the caller can report progress. */
	@FunctionalInterface
	public interface Progress {
		Progress NONE = outcome -> { };

		void candidateDone(CandidateOutcome outcome);
	}

	public enum CandidateOutcome { GENERATED, REUSED, FAILED }

	/** What one job's run did. {@code skipped}: the job had no embedding yet, so nothing ran. */
	public record JobOutcome(String jobId, int candidates, int generated, int reused, int failed, boolean skipped) {
	}

	/** What running a job would cost: its top-N candidates, split into reports to generate and reports reused. */
	public record JobEstimate(String jobId, String title, int candidates, int newReports, int reusedReports, boolean indexing) {
	}

	/**
	 * Loads and checks the jobs of a run: each must exist in the tenant and be open.
	 * 400 when none is given, too many are given, or one is closed.
	 */
	public List<JobPostDTO> openJobs(Collection<String> jobIds) throws QorvaException {
		var ids = jobIds == null ? List.<String>of() : jobIds.stream().filter(StringUtils::hasText).distinct().toList();
		if (ids.isEmpty()) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.MATCHING_NO_JOBS);
		}
		if (ids.size() > MAX_JOBS_PER_RUN) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.MATCHING_TOO_MANY_JOBS, MAX_JOBS_PER_RUN);
		}
		var jobs = new ArrayList<JobPostDTO>();
		for (var id : ids) {
			var job = jobPostService.findOneById(id);
			if (!JobPostStatusEnum.OPEN.getStatus().equals(job.getStatus())) {
				throw QorvaErrors.badRequest(QorvaErrorCodes.MATCHING_JOB_NOT_OPEN, job.getTitle());
			}
			jobs.add(job);
		}
		return jobs;
	}

	/**
	 * The cost of matching {@code jobs} at {@code topN} without spending anything: a vector search per job,
	 * then each candidate's fingerprint compared with their stored report. No model call.
	 */
	public List<JobEstimate> estimate(List<JobPostDTO> jobs, int topN, String languageCode) throws QorvaException {
		var language = normalise(languageCode);
		var reportVersion = openAIService.reportVersion();
		var estimates = new ArrayList<JobEstimate>();
		for (var job : jobs) {
			if (!hasEmbedding(job)) {
				estimates.add(new JobEstimate(job.getId(), job.getTitle(), 0, 0, 0, true));
				continue;
			}
			var candidates = cvService.match(job, topN);
			var existing = matchingReportService.statesForJob(job.getTenantId(), job.getId());
			var jobFingerprint = MatchingFingerprint.job(job);
			int reused = 0;
			for (var candidate : candidates) {
				var state = existing.get(candidate.cv().getId());
				var input = MatchingFingerprint.input(MatchingFingerprint.cv(candidate.cv()), jobFingerprint, language, reportVersion);
				if (state != null && input.equals(state.inputFingerprint())) {
					reused++;
				}
			}
			estimates.add(new JobEstimate(job.getId(), job.getTitle(), candidates.size(), candidates.size() - reused, reused, false));
		}
		return estimates;
	}

	/**
	 * Matching for the chosen open jobs, run in the caller's thread (Copilot's start_screening). Each job runs
	 * at {@code topN} when given, else at its own last Top N or the plan default. Checks the plan itself — this
	 * path has no controller guard. Returns the jobs screened; closed or unknown jobs are skipped.
	 */
	public List<JobPostDTO> screenJobs(String tenantId, List<String> jobIds, Integer topN, String languageCode) throws QorvaException {
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
		var topNs = new HashMap<String, Integer>();
		int newReports = 0;
		for (var job : jobs) {
			int n = topNPolicy.resolve(tenantId, topN, job.getMatchingTopN());
			topNs.put(job.getId(), n);
			newReports += estimate(List.of(job), n, languageCode).getFirst().newReports();
		}
		if (!usageMonitoringService.hasCapacityFor(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, newReports)) {
			throw QorvaErrors.forbidden(QorvaErrorCodes.USAGE_SCREENING_LIMIT_EXCEEDED);
		}
		log.info("Screening {} chosen job(s) for tenant={}", jobs.size(), tenantId);
		try (var executor = TenantScope.propagating(Executors.newVirtualThreadPerTaskExecutor())) {
			var jobFutures = jobs.stream()
				.map(jp -> CompletableFuture.runAsync(() -> matchJob(jp, topNs.get(jp.getId()), languageCode, executor, Progress.NONE), executor))
				.toArray(CompletableFuture[]::new);
			CompletableFuture.allOf(jobFutures).join();
		}
		return List.copyOf(jobs);
	}

	/**
	 * Brings one job's reports in step with its current top {@code topN}: reuses unchanged reports, generates
	 * the rest (metered, ATS write-back queued), marks the ones that left the top N outdated, and records the
	 * run on the job. Candidates run concurrently on {@code executor}; a failed candidate is logged and keeps
	 * the job flagged so it can be re-run. A job without an embedding (Atlas still indexing) is skipped.
	 */
	public JobOutcome matchJob(JobPostDTO job, int topN, String languageCode, Executor executor, Progress progress) {
		if (!hasEmbedding(job)) {
			log.warn("Skipping job post {} - embedding not yet available", job.getId());
			return new JobOutcome(job.getId(), 0, 0, 0, 0, true);
		}
		var tenantId = job.getTenantId();
		var language = normalise(languageCode);
		List<CVService.ScoredCv> candidates;
		try {
			candidates = cvService.match(job, topN);
		} catch (QorvaException e) {
			log.error("Error matching job post {}", job.getId(), e);
			return new JobOutcome(job.getId(), 0, 0, 0, 1, false);
		}
		var existing = matchingReportService.statesForJob(tenantId, job.getId());
		var jobFingerprint = MatchingFingerprint.job(job);
		var reportVersion = openAIService.reportVersion();
		var generated = new AtomicInteger();
		var reused = new AtomicInteger();
		var failed = new AtomicInteger();

		var futures = candidates.stream()
			.map(candidate -> CompletableFuture.runAsync(() -> {
				var outcome = processCandidate(candidate.cv(), job, existing.get(candidate.cv().getId()),
					jobFingerprint, language, reportVersion);
				(switch (outcome) {
					case GENERATED -> generated;
					case REUSED -> reused;
					case FAILED -> failed;
				}).incrementAndGet();
				progress.candidateDone(outcome);
			}, executor))
			.toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(futures).join();

		var kept = new HashSet<String>();
		candidates.forEach(c -> kept.add(c.cv().getId()));
		long retired = matchingReportService.markOutdated(tenantId, job.getId(), kept, ids -> outdatedReasons(job, ids));

		double cutoff = candidates.size() >= topN
			? candidates.getLast().score()
			: SimilaritySearchRepository.MIN_MATCH_SCORE;
		jobPostService.recordRun(tenantId, job.getId(), topN, cutoff, failed.get() == 0);
		log.info("Matching done for job post {} (top {}): {} candidates, {} generated, {} reused, {} failed, {} outdated",
			job.getId(), topN, candidates.size(), generated.get(), reused.get(), failed.get(), retired);
		return new JobOutcome(job.getId(), candidates.size(), generated.get(), reused.get(), failed.get(), false);
	}

	private CandidateOutcome processCandidate(CVDTO cv, JobPostDTO job, ReportState existing, String jobFingerprint,
	                                          String language, String reportVersion) {
		try {
			var cvFingerprint = MatchingFingerprint.cv(cv);
			var input = MatchingFingerprint.input(cvFingerprint, jobFingerprint, language, reportVersion);
			if (existing != null && input.equals(existing.inputFingerprint())) {
				if (existing.outdated()) {
					matchingReportService.markCurrent(job.getTenantId(), existing.id());
				}
				return CandidateOutcome.REUSED;
			}
			var analysisDetails = generateReportWithLimit(cv, job, language);
			incrementUsageSilently(job.getTenantId());
			matchingReportService.saveGenerated(job, analysisDetails, cv, input, cvFingerprint, existing);
			atsWriteBackService.maybeEnqueue(cv, job, analysisDetails);
			return CandidateOutcome.GENERATED;
		} catch (Exception e) {
			log.error("Error processing candidate {} for job post {}", cv.getId(), job.getId(), e);
			return CandidateOutcome.FAILED;
		}
	}

	/**
	 * Why each candidate left the top N: no longer eligible (archived, deleted, or outside the job's
	 * availability filters) or simply ranked out.
	 */
	private Map<String, String> outdatedReasons(JobPostDTO job, Collection<String> candidateIds) {
		var eligible = cvService.stillEligible(candidateIds, job.getScoringRules());
		var reasons = new HashMap<String, String>();
		for (var id : candidateIds) {
			reasons.put(id, eligible.contains(id)
				? MatchingOutdatedReasonEnum.RANKED_OUT.name()
				: MatchingOutdatedReasonEnum.NO_LONGER_ELIGIBLE.name());
		}
		return reasons;
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

	private static boolean hasEmbedding(JobPostDTO job) {
		return job.getEmbedding() != null && job.getEmbedding().length > 0;
	}

	/** The report prompt's own default; part of the fingerprint, so it must be stable. */
	static String normalise(String languageCode) {
		return StringUtils.hasText(languageCode) ? languageCode.trim() : "en";
	}

	private void incrementUsageSilently(String tenantId) {
		try {
			usageMonitoringService.incrementUsage(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, 1);
		} catch (Exception e) {
			log.warn("Failed to increment {} usage for tenant={}", UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, tenantId, e);
		}
	}
}
