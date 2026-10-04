package ai.qorva.core.service;

import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.repository.BackgroundJobRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.MatchingRunData;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Matching runs the recruiter starts: pick open jobs and a Top N, see what it costs, then a background job
 * ({@link BackgroundJob#TYPE_MATCHING}) does the work while the app follows its progress. Several runs may
 * queue for a tenant, but a job is never in two active runs at once.
 */
@Slf4j
@Service
public class MatchingRunService {

	private static final List<String> ACTIVE_STATUSES = List.of(BackgroundJob.STATUS_PENDING, BackgroundJob.STATUS_RUNNING);
	/** Matches BackgroundJobWorker's lease; every finished candidate extends it. */
	private static final Duration LEASE = Duration.ofMinutes(2);
	private static final int HEARTBEAT_EVERY = 5;
	private static final String RUN_NOT_FOUND = QorvaErrorCodes.MATCHING_RUN_NOT_FOUND;

	private final BackgroundJobRepository jobRepository;
	private final BackgroundJobQueries jobs;
	private final MongoTemplate mongoTemplate;
	private final AIScreeningService screeningService;
	private final MatchingTopNPolicy topNPolicy;
	private final UsageMonitoringService usageMonitoringService;
	private final JobPostService jobPostService;

	public MatchingRunService(BackgroundJobRepository jobRepository, MongoTemplate mongoTemplate,
	                          AIScreeningService screeningService, MatchingTopNPolicy topNPolicy,
	                          UsageMonitoringService usageMonitoringService, JobPostService jobPostService) {
		this.jobRepository = jobRepository;
		this.jobs = new BackgroundJobQueries(jobRepository);
		this.mongoTemplate = mongoTemplate;
		this.screeningService = screeningService;
		this.topNPolicy = topNPolicy;
		this.usageMonitoringService = usageMonitoringService;
		this.jobPostService = jobPostService;
	}

	public MatchingRunData.Options options(String tenantId) {
		var limits = topNPolicy.limitsFor(tenantId);
		return new MatchingRunData.Options(limits.allowed(), limits.defaultTopN(), limits.max());
	}

	public MatchingRunData.Estimate estimate(String tenantId, MatchingRunData.Request request, String language) throws QorvaException {
		var jobsToRun = screeningService.openJobs(request.jobIds());
		int topN = topNPolicy.resolve(tenantId, request.topN(), null);
		return estimate(tenantId, jobsToRun, topN, language);
	}

	private MatchingRunData.Estimate estimate(String tenantId, List<JobPostDTO> jobsToRun, int topN, String language) throws QorvaException {
		var limits = topNPolicy.limitsFor(tenantId);
		var perJob = screeningService.estimate(jobsToRun, topN, language).stream()
			.map(e -> new MatchingRunData.JobEstimate(e.jobId(), e.title(), e.candidates(), e.newReports(), e.reusedReports(), e.indexing()))
			.toList();
		int candidates = perJob.stream().mapToInt(MatchingRunData.JobEstimate::candidates).sum();
		int newReports = perJob.stream().mapToInt(MatchingRunData.JobEstimate::newReports).sum();
		int reused = perJob.stream().mapToInt(MatchingRunData.JobEstimate::reusedReports).sum();
		return new MatchingRunData.Estimate(topN, limits.allowed(), limits.defaultTopN(), candidates, newReports, reused,
			newReports, usageMonitoringService.remaining(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS), perJob);
	}

	/**
	 * Queues a run. 400 for no/closed jobs or an invalid Top N, 403 for a Top N above the plan, 409 when one of the
	 * jobs is already in an active run, 400 when the new reports exceed what the period has left.
	 */
	public MatchingRunData.SubmitResponse submit(String tenantId, String createdBy, MatchingRunData.Request request,
	                                             String language) throws QorvaException {
		var jobsToRun = screeningService.openJobs(request.jobIds());
		int topN = topNPolicy.resolve(tenantId, request.topN(), null);
		if (anyInActiveRun(tenantId, jobsToRun)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.MATCHING_RUN_ACTIVE);
		}
		var estimate = estimate(tenantId, jobsToRun, topN, language);
		if (estimate.remainingActions() != null && estimate.estimatedActions() > estimate.remainingActions()) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.MATCHING_QUOTA_EXCEEDED, estimate.estimatedActions(), estimate.remainingActions());
		}
		var run = jobRepository.save(BackgroundJob.builder()
			.tenantId(tenantId)
			.type(BackgroundJob.TYPE_MATCHING)
			.jobIds(jobsToRun.stream().map(JobPostDTO::getId).toList())
			.topN(topN)
			.language(AIScreeningService.normalise(language))
			.status(BackgroundJob.STATUS_PENDING)
			.total(estimate.candidates())
			.errorSamples(List.of())
			.createdBy(createdBy)
			.createdAt(Instant.now())
			.build());
		log.info("Matching run {} submitted: {} job(s), top {}, ~{} new report(s), tenant={}",
			run.getId(), jobsToRun.size(), topN, estimate.newReports(), tenantId);
		return new MatchingRunData.SubmitResponse(estimate, MatchingRunData.RunView.from(run));
	}

	/**
	 * The former one-click run, kept for app versions still calling {@code /ai/start-screening}: every flagged
	 * open job not already in a run, at the plan default. Returns null when there is nothing to match.
	 */
	public MatchingRunData.SubmitResponse submitFlagged(String tenantId, String createdBy, String language) throws QorvaException {
		var busy = activeJobIds(tenantId);
		var flagged = jobPostService.findJobPostsNeedingReports(tenantId).stream()
			.map(JobPostDTO::getId)
			.filter(id -> !busy.contains(id))
			.limit(AIScreeningService.MAX_JOBS_PER_RUN)
			.toList();
		if (flagged.isEmpty()) {
			return null;
		}
		return submit(tenantId, createdBy, new MatchingRunData.Request(flagged, null), language);
	}

	public MatchingRunData.RunView get(String tenantId, String runId) throws QorvaException {
		var run = jobs.require(tenantId, runId, RUN_NOT_FOUND);
		if (!BackgroundJob.TYPE_MATCHING.equals(run.getType())) {
			throw QorvaErrors.notFound(RUN_NOT_FOUND);
		}
		return MatchingRunData.RunView.from(run);
	}

	public MatchingRunData.RunView cancel(String tenantId, String runId) throws QorvaException {
		get(tenantId, runId);
		var view = jobs.cancel(tenantId, runId, RUN_NOT_FOUND, ACTIVE_STATUSES, run -> { });
		return get(tenantId, view.id());
	}

	/** Runs still queued or in progress, so a reopened page can follow them. */
	public MatchingRunData.RunList active(String tenantId) {
		return new MatchingRunData.RunList(jobRepository
			.findByTenantIdAndTypeAndStatusIn(tenantId, BackgroundJob.TYPE_MATCHING, ACTIVE_STATUSES).stream()
			.map(MatchingRunData.RunView::from)
			.toList());
	}

	/** The latest runs, newest first. */
	public MatchingRunData.RunList recent(String tenantId) {
		return new MatchingRunData.RunList(jobRepository
			.findByTenantIdAndTypeOrderByCreatedAtDesc(tenantId, BackgroundJob.TYPE_MATCHING, PageRequest.of(0, 10)).stream()
			.map(MatchingRunData.RunView::from)
			.toList());
	}

	private boolean anyInActiveRun(String tenantId, List<JobPostDTO> jobsToRun) {
		var busy = activeJobIds(tenantId);
		return jobsToRun.stream().anyMatch(j -> busy.contains(j.getId()));
	}

	private HashSet<String> activeJobIds(String tenantId) {
		var busy = new HashSet<String>();
		jobRepository.findByTenantIdAndTypeAndStatusIn(tenantId, BackgroundJob.TYPE_MATCHING, ACTIVE_STATUSES)
			.forEach(run -> {
				if (run.getJobIds() != null) busy.addAll(run.getJobIds());
			});
		return busy;
	}

	/**
	 * Worker entry point (runs in the tenant's scope). Jobs are matched one after the other, candidates in
	 * parallel; a reclaimed run starts over, which costs nothing for what was already done (those reports are
	 * reused). Stops early when cancelled or when the period's actions run out.
	 */
	public void execute(BackgroundJob run) {
		var tenantId = run.getTenantId();
		var generated = new AtomicLong();
		var reused = new AtomicLong();
		var failed = new AtomicLong();
		var processed = new AtomicLong();
		long skippedJobs = 0;
		String failureReason = null;
		int topN = run.getTopN() != null ? run.getTopN() : topNPolicy.limitsFor(tenantId).defaultTopN();

		try (var executor = TenantScope.propagating(Executors.newVirtualThreadPerTaskExecutor())) {
			for (var jobId : run.getJobIds() != null ? run.getJobIds() : List.<String>of()) {
				if (isCancelled(run.getId())) {
					log.info("Matching run {} cancelled after {} candidates", run.getId(), processed.get());
					return;
				}
				if (usageMonitoringService.hasExceededLimit(tenantId, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS)) {
					failureReason = "quota_exceeded";
					break;
				}
				JobPostDTO job;
				try {
					job = jobPostService.findOneById(jobId);
				} catch (QorvaException e) {
					skippedJobs++;
					continue;
				}
				if (!JobPostStatusEnum.OPEN.getStatus().equals(job.getStatus())) {
					skippedJobs++;
					continue;
				}
				var outcome = screeningService.matchJob(job, topN, run.getLanguage(), executor, candidate -> {
					switch (candidate) {
						case GENERATED -> generated.incrementAndGet();
						case REUSED -> reused.incrementAndGet();
						case FAILED -> failed.incrementAndGet();
					}
					if (processed.incrementAndGet() % HEARTBEAT_EVERY == 0) {
						heartbeat(run.getId(), processed.get(), generated.get(), reused.get(), failed.get());
					}
				});
				if (outcome.skipped()) {
					skippedJobs++;
				}
				heartbeat(run.getId(), processed.get(), generated.get(), reused.get(), failed.get());
			}
		}

		var status = failed.get() > 0 || failureReason != null || skippedJobs > 0
			? BackgroundJob.STATUS_COMPLETED_WITH_ERRORS
			: BackgroundJob.STATUS_COMPLETED;
		var update = new Update()
			.set("status", status)
			.set("processed", processed.get())
			.set("succeeded", generated.get())
			.set("reused", reused.get())
			.set("failed", failed.get())
			.set("skipped", skippedJobs)
			.set("finishedAt", Instant.now());
		if (failureReason != null) {
			update.set("failureReason", failureReason);
		}
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(run.getId())), update, BackgroundJob.class);
		log.info("Matching run {} finished: {} — {} generated, {} reused, {} failed, {} job(s) skipped{}", run.getId(),
			status, generated.get(), reused.get(), failed.get(), skippedJobs, failureReason != null ? " (" + failureReason + ")" : "");
	}

	private boolean isCancelled(String runId) {
		var current = mongoTemplate.findById(runId, BackgroundJob.class);
		return current == null || BackgroundJob.STATUS_CANCELLED.equals(current.getStatus());
	}

	private void heartbeat(String runId, long processed, long generated, long reused, long failed) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(runId)),
			new Update()
				.set("processed", processed)
				.set("succeeded", generated)
				.set("reused", reused)
				.set("failed", failed)
				.set("leaseExpiresAt", Instant.now().plus(LEASE)),
			BackgroundJob.class);
	}
}
