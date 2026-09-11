package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.dao.repository.BackgroundJobRepository;
import ai.qorva.core.dto.BackgroundJobData;
import ai.qorva.core.dto.common.AtsRef;
import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.LibraryQualityCacheEvictor;
import ai.qorva.core.service.ScoringRulesPrefillService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.ats.AtsModels.AtsCandidate;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * The ATS sync engine. Runs inside a BackgroundJob claimed by BackgroundJobWorker, so
 * lease/heartbeat/crash-resume semantics match every other async job. One run drains
 * the connection's job stream (optional) and candidate delta stream; each new or
 * changed resume goes through the same CVService.processFile pipeline as uploads.
 * Imports gate on the screening-action quota exactly like bulk upload — the quota is
 * consumed at screening time, never at import time.
 */
@Slf4j
@Service
public class AtsSyncService {

	public static final String TRIGGER_SCHEDULED = "SCHEDULED";
	public static final String TRIGGER_MANUAL = "MANUAL";
	public static final String TRIGGER_WEBHOOK = "WEBHOOK";

	private static final List<String> ACTIVE_STATUSES =
		List.of(BackgroundJob.STATUS_PENDING, BackgroundJob.STATUS_RUNNING);
	private static final Duration LEASE = Duration.ofMinutes(2);
	private static final int ERROR_SAMPLE_CAP = 20;
	private static final int CONTROL_CHECK_EVERY = 10;

	private final AtsConnectionRepository connectionRepository;
	private final BackgroundJobRepository jobRepository;
	private final AtsConnectionService connectionService;
	private final AtsConnectorRegistry registry;
	private final AtsOauthService oauthService;
	private final CVService cvService;
	private final JobPostService jobPostService;
	private final UsageMonitoringService usageMonitoringService;
	private final LibraryQualityCacheEvictor cacheEvictor;
	private final ScoringRulesPrefillService scoringRulesPrefillService;
	private final AtsProperties properties;
	private final MongoTemplate mongoTemplate;

	public AtsSyncService(
		AtsConnectionRepository connectionRepository,
		BackgroundJobRepository jobRepository,
		AtsConnectionService connectionService,
		AtsConnectorRegistry registry,
		AtsOauthService oauthService,
		CVService cvService,
		JobPostService jobPostService,
		UsageMonitoringService usageMonitoringService,
		LibraryQualityCacheEvictor cacheEvictor,
		ScoringRulesPrefillService scoringRulesPrefillService,
		AtsProperties properties,
		MongoTemplate mongoTemplate
	) {
		this.connectionRepository = connectionRepository;
		this.jobRepository = jobRepository;
		this.connectionService = connectionService;
		this.registry = registry;
		this.oauthService = oauthService;
		this.cvService = cvService;
		this.jobPostService = jobPostService;
		this.usageMonitoringService = usageMonitoringService;
		this.cacheEvictor = cacheEvictor;
		this.scoringRulesPrefillService = scoringRulesPrefillService;
		this.properties = properties;
		this.mongoTemplate = mongoTemplate;
	}

	// ------------------------------------------------------------------ enqueue / list

	public BackgroundJobData.JobView enqueue(String tenantId, String connectionId, String trigger, String createdBy)
		throws QorvaException {
		var connection = connectionService.findOwned(tenantId, connectionId);
		if (!AtsConnection.STATUS_CONNECTED.equals(connection.getStatus())) {
			throw new QorvaException(QorvaErrorCodes.ATS_CONNECTION_NOT_CONNECTED,
				HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}
		if (jobRepository.existsByConnectionIdAndStatusIn(connectionId, ACTIVE_STATUSES)) {
			throw new QorvaException(QorvaErrorCodes.ATS_SYNC_ACTIVE_EXISTS,
				HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}
		var job = jobRepository.save(BackgroundJob.builder()
			.tenantId(tenantId)
			.type(BackgroundJob.TYPE_ATS_SYNC)
			.status(BackgroundJob.STATUS_PENDING)
			.connectionId(connectionId)
			.trigger(trigger)
			.errorSamples(List.of())
			.createdBy(createdBy)
			.createdAt(Instant.now())
			.build());
		log.info("ATS sync job {} enqueued for connection {} ({})", job.getId(), connectionId, trigger);
		return BackgroundJobData.JobView.from(job);
	}

	/** Webhook path: silently skips when a run is already queued — events are only hints. */
	public void enqueueQuietly(AtsConnection connection, String trigger) {
		if (jobRepository.existsByConnectionIdAndStatusIn(connection.getId(), ACTIVE_STATUSES)) {
			return;
		}
		jobRepository.save(BackgroundJob.builder()
			.tenantId(connection.getTenantId())
			.type(BackgroundJob.TYPE_ATS_SYNC)
			.status(BackgroundJob.STATUS_PENDING)
			.connectionId(connection.getId())
			.trigger(trigger)
			.errorSamples(List.of())
			.createdBy("ats-" + trigger.toLowerCase())
			.createdAt(Instant.now())
			.build());
	}

	public BackgroundJobData.JobList listRuns(String tenantId, String connectionId) throws QorvaException {
		connectionService.findOwned(tenantId, connectionId);
		var runs = jobRepository
			.findByTenantIdAndConnectionIdOrderByCreatedAtDesc(tenantId, connectionId, PageRequest.of(0, 10))
			.stream()
			.map(BackgroundJobData.JobView::from)
			.toList();
		return new BackgroundJobData.JobList(runs);
	}

	// ------------------------------------------------------------------ execution

	/** Entry point called by BackgroundJobWorker with a claimed RUNNING job. */
	public void executeSync(BackgroundJob job) {
		var connectionOpt = connectionRepository.findById(job.getConnectionId());
		if (connectionOpt.isEmpty()) {
			finish(job.getId(), BackgroundJob.STATUS_FAILED, new Counters(), "connection_deleted");
			return;
		}
		var connection = connectionOpt.get();
		var counters = new Counters();
		counters.processed = job.getProcessed();
		counters.succeeded = job.getSucceeded();
		counters.failed = job.getFailed();
		counters.skipped = job.getSkipped();
		if (job.getErrorSamples() != null) {
			counters.errorSamples.addAll(job.getErrorSamples());
		}

		try {
			var provider = AtsProviderEnum.fromValue(connection.getProvider());
			var connector = registry.get(provider);
			var credentials = oauthService.ensureFreshToken(connection, connectionService.decryptCredentials(connection));
			var runStart = Instant.now();

			var settings = connection.getSettings() != null ? connection.getSettings() : new AtsConnection.Settings();
			if (Boolean.TRUE.equals(settings.getImportJobs())) {
				syncJobs(connection, connector, credentials, counters);
			}

			var outcome = syncCandidates(job, connection, connector, credentials, counters, settings);

			// Advance the durable cursor only after a clean drain — a guard/quota stop
			// re-reads from the same point next run; the atsRefs dedup makes that cheap.
			if (outcome == Outcome.DRAINED) {
				var sync = connection.getSyncState() != null ? connection.getSyncState() : new AtsConnection.SyncState();
				sync.setCandidatesCursor(runStart.toString());
				sync.setLastSyncAt(runStart);
				sync.setLastSyncError(null);
				connection.setSyncState(sync);
				connectionRepository.save(connection);
			}

			if (counters.succeeded > 0) {
				try {
					jobPostService.markOpenJobPostsAsNeedingReports(connection.getTenantId());
				} catch (Exception e) {
					log.warn("ATS sync {} — could not mark job posts as needing reports", job.getId(), e);
				}
				cacheEvictor.evict(connection.getTenantId());
			}

			switch (outcome) {
				case DRAINED -> finish(job.getId(),
					counters.failed > 0 ? BackgroundJob.STATUS_COMPLETED_WITH_ERRORS : BackgroundJob.STATUS_COMPLETED,
					counters, null);
				case QUOTA_EXHAUSTED -> finish(job.getId(), BackgroundJob.STATUS_COMPLETED_WITH_ERRORS,
					counters, "quota_exceeded");
				case INITIAL_SYNC_GUARD -> finish(job.getId(), BackgroundJob.STATUS_COMPLETED_WITH_ERRORS,
					counters, "initial_sync_guard");
				case CANCELLED -> log.info("ATS sync {} cancelled after {} candidates", job.getId(), counters.processed);
			}
			log.info("ATS sync {} done: {} imported, {} skipped, {} failed", job.getId(),
				counters.succeeded, counters.skipped, counters.failed);
		} catch (QorvaException e) {
			if (QorvaErrorCodes.ATS_AUTH_FAILED.equals(e.getMessage())) {
				connectionService.markAuthError(connection, "auth_failed");
				finish(job.getId(), BackgroundJob.STATUS_FAILED, counters, "auth_error");
			} else {
				recordSyncError(connection, e.getMessage());
				finish(job.getId(), BackgroundJob.STATUS_FAILED, counters, "provider_error");
			}
			log.warn("ATS sync {} failed: {}", job.getId(), e.getMessage());
		} catch (Exception e) {
			recordSyncError(connection, e.getMessage());
			finish(job.getId(), BackgroundJob.STATUS_FAILED, counters, "internal_error");
			log.error("ATS sync {} crashed", job.getId(), e);
		}
	}

	private enum Outcome { DRAINED, QUOTA_EXHAUSTED, INITIAL_SYNC_GUARD, CANCELLED }

	private void syncJobs(AtsConnection connection, AtsConnector connector, AtsCredentials credentials,
		Counters counters) throws QorvaException {
		String cursor = null;
		do {
			var page = connector.listJobs(credentials, cursor);
			for (var atsJob : page.items()) {
				if (atsJob.externalId() == null || !StringUtils.hasText(atsJob.title())) {
					continue;
				}
				try {
					upsertJobPost(connection, atsJob);
				} catch (Exception e) {
					// Candidate imports already survive one bad record; jobs did not, so a single
					// unusable offer failed the whole run and took the candidate sync with it.
					log.warn("ATS sync — could not import job {} on {}: {}",
						atsJob.externalId(), connection.getProvider(), e.getMessage());
					sample(counters, "Job " + atsJob.externalId() + ": " + e.getMessage());
				}
			}
			cursor = page.nextCursor();
		} while (cursor != null);
	}

	// Package-private so the import rules can be asserted without driving a whole sync run.
	void upsertJobPost(AtsConnection connection, AtsModels.AtsJob atsJob) {
		var tenantId = new ObjectId(connection.getTenantId());
		var jobReference = jobReference(atsJob);
		var query = Query.query(Criteria.where("tenantId").is(tenantId)
			.and("atsRef.provider").is(connection.getProvider())
			.and("atsRef.externalId").is(atsJob.externalId()));
		var existing = mongoTemplate.findOne(query, JobPost.class);

		// jobReference is unique per tenant, so a row already holding this one has to be adopted
		// rather than inserted beside — the insert would only fail on the index. A job whose
		// atsRef was dropped by an earlier update lands here and gets re-linked below.
		if (existing == null) {
			var byReference = Query.query(Criteria.where("tenantId").is(tenantId)
				.and("jobReference").is(jobReference));
			var orphan = mongoTemplate.findOne(byReference, JobPost.class);
			if (orphan != null) {
				log.info("ATS sync — re-linking job {} to {} {}",
					orphan.getId(), connection.getProvider(), atsJob.externalId());
				existing = orphan;
				query = byReference;
			}
		}

		var status = atsJob.open() ? JobPostStatusEnum.OPEN.getStatus() : JobPostStatusEnum.CLOSED.getStatus();
		if (existing != null) {
			var update = new Update()
				.set("title", atsJob.title())
				.set("status", status)
				// Rewritten every run: it re-links an orphan and keeps lastImportedAt honest.
				.set("atsRef", atsRef(connection, atsJob))
				.set("lastUpdatedAt", Instant.now());
			if (StringUtils.hasText(atsJob.description())) {
				update.set("description", atsJob.description());
			}
			// Backfills a job imported before its criteria could be drafted — one whose
			// description only arrived on a later sync. Rules already there are left alone:
			// they may have been tuned by hand, and an import must not undo that.
			if (existing.getScoringRules() == null) {
				var rules = suggestScoringRules(atsJob);
				if (rules != null) {
					update.set("scoringRules", rules);
				}
			}
			mongoTemplate.updateFirst(query, update, JobPost.class);
			return;
		}
		var jobPost = new JobPost();
		jobPost.setTenantId(connection.getTenantId());
		jobPost.setTitle(atsJob.title());
		jobPost.setDescription(atsJob.description());
		jobPost.setScoringRules(suggestScoringRules(atsJob));
		jobPost.setJobReference(jobReference);
		jobPost.setStatus(status);
		jobPost.setMatchingReportsNeeded(atsJob.open());
		jobPost.setAtsRef(atsRef(connection, atsJob));
		jobPost.setCreatedAt(Instant.now());
		jobPost.setCreatedBy("ats-sync");
		mongoTemplate.insert(jobPost);
	}

	/** The tenant-unique reference an imported job carries, derived from the ATS record's own id. */
	private static String jobReference(AtsModels.AtsJob atsJob) {
		return "ATS-" + atsJob.externalId();
	}

	private static AtsRef atsRef(AtsConnection connection, AtsModels.AtsJob atsJob) {
		return AtsRef.builder()
			.provider(connection.getProvider())
			.connectionId(connection.getId())
			.externalId(atsJob.externalId())
			.externalUrl(atsJob.externalUrl())
			.lastImportedAt(Instant.now())
			.build();
	}

	/**
	 * Drafts the matching criteria the creation wizard would have produced. An imported job
	 * never passes through that wizard, so without this it is screened — it is flagged
	 * matchingReportsNeeded when open — against no criteria at all: an unfiltered shortlist in
	 * CVService.match and an empty scoring_rules in the report prompt, with nothing to say so.
	 *
	 * <p>Unmetered on purpose: the wizard bills a screening action because a recruiter asked
	 * for the suggestion, whereas an import drafts rules for jobs nobody requested and would
	 * otherwise charge a tenant once per offer on the sync.</p>
	 *
	 * <p>Returns null rather than throwing — a job worth importing must not be lost because the
	 * model was unavailable, and the next sync backfills what this one could not draft.</p>
	 */
	private ScoringRules suggestScoringRules(AtsModels.AtsJob atsJob) {
		if (!StringUtils.hasText(atsJob.description())) {
			return null;
		}
		try {
			return scoringRulesPrefillService.suggestUnmetered(atsJob.title(), atsJob.description());
		} catch (Exception e) {
			log.warn("ATS sync — could not draft scoring rules for job {}: {}",
				atsJob.externalId(), e.getMessage());
			return null;
		}
	}

	private Outcome syncCandidates(BackgroundJob job, AtsConnection connection, AtsConnector connector,
		AtsCredentials credentials, Counters counters, AtsConnection.Settings settings) throws QorvaException {
		var tenantId = connection.getTenantId();
		var syncState = connection.getSyncState() != null ? connection.getSyncState() : new AtsConnection.SyncState();
		boolean initialSync = !StringUtils.hasText(syncState.getCandidatesCursor());
		boolean guardActive = initialSync && !Boolean.TRUE.equals(settings.getInitialSyncConfirmed());
		String cursor = syncState.getCandidatesCursor();
		int seen = 0;

		do {
			var page = connector.listCandidates(credentials, cursor);
			for (var candidate : page.items()) {
				if (seen % CONTROL_CHECK_EVERY == 0) {
					if (isCancelled(job.getId())) {
						return Outcome.CANCELLED;
					}
					if (usageMonitoringService.hasExceededLimit(tenantId,
						UsageMonitoringService.FeatureKey.SCREENING_ACTIONS)) {
						return Outcome.QUOTA_EXHAUSTED;
					}
				}
				if (guardActive && seen >= properties.getInitialSyncGuard()) {
					return Outcome.INITIAL_SYNC_GUARD;
				}
				seen++;
				importCandidate(connection, connector, credentials, candidate, counters);
				if (counters.processed % CONTROL_CHECK_EVERY == 0) {
					heartbeat(job.getId(), counters);
				}
			}
			cursor = page.nextCursor();
			heartbeat(job.getId(), counters);
		} while (cursor != null);

		return Outcome.DRAINED;
	}

	private void importCandidate(AtsConnection connection, AtsConnector connector, AtsCredentials credentials,
		AtsCandidate candidate, Counters counters) {
		counters.processed++;
		counters.total++;
		try {
			if (candidate.externalId() == null) {
				counters.skipped++;
				return;
			}
			var existing = findLinkedCv(connection, candidate.externalId());
			if (existing != null && candidate.updatedAt() != null) {
				var ref = linkedRef(existing, connection, candidate.externalId());
				if (ref != null && ref.getLastImportedAt() != null
					&& !candidate.updatedAt().isAfter(ref.getLastImportedAt())) {
					counters.skipped++;
					return;
				}
			}

			var resume = connector.downloadResume(credentials, candidate);
			if (resume == null) {
				counters.skipped++;
				sample(counters, displayName(candidate) + ": no resume file");
				return;
			}
			var hash = sha256(resume.bytes());
			if (existing != null) {
				var ref = linkedRef(existing, connection, candidate.externalId());
				if (ref != null && hash.equals(ref.getResumeContentHash())) {
					touchRef(existing, connection, candidate.externalId(), hash);
					counters.skipped++;
					return;
				}
			}

			var created = cvService.processFile(
				resume.bytes(), resume.filename(), resume.contentType(), connection.getTenantId());
			if (existing != null) {
				// Same person, new resume: keep the fresh extraction, retire the old CV
				// (replaceDuplicate merges tags and cascades reports/chats/S3).
				created = cvService.replaceDuplicate(created.getId(), existing.getId(), connection.getTenantId());
			}
			attachRef(created.getId(), connection, candidate, hash);
			counters.succeeded++;
		} catch (Exception e) {
			counters.failed++;
			sample(counters, displayName(candidate) + ": " + e.getMessage());
			log.warn("ATS sync — import failed for candidate {} on {}", candidate.externalId(),
				connection.getProvider(), e);
		}
	}

	// ------------------------------------------------------------------ CV link helpers

	private CV findLinkedCv(AtsConnection connection, String externalId) {
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(connection.getTenantId()))
			.and("atsRefs").elemMatch(Criteria.where("provider").is(connection.getProvider())
				.and("externalId").is(externalId)));
		query.fields().include("_id", "atsRefs");
		return mongoTemplate.findOne(query, CV.class);
	}

	private AtsRef linkedRef(CV cv, AtsConnection connection, String externalId) {
		if (cv.getAtsRefs() == null) {
			return null;
		}
		return cv.getAtsRefs().stream()
			.filter(r -> connection.getProvider().equals(r.getProvider()) && externalId.equals(r.getExternalId()))
			.findFirst()
			.orElse(null);
	}

	private void attachRef(String cvId, AtsConnection connection, AtsCandidate candidate, String hash) {
		var ref = AtsRef.builder()
			.provider(connection.getProvider())
			.connectionId(connection.getId())
			.externalId(candidate.externalId())
			.externalApplicationId(candidate.externalApplicationId())
			.externalUrl(candidate.externalUrl())
			.resumeContentHash(hash)
			.lastImportedAt(Instant.now())
			.build();
		// Replace any stale ref for this provider before pushing the fresh one.
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(cvId)),
			new Update().pull("atsRefs",
				Query.query(Criteria.where("provider").is(connection.getProvider())
					.and("externalId").is(candidate.externalId())).getQueryObject()),
			CV.class);
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(cvId)),
			new Update().push("atsRefs", ref), CV.class);
	}

	private void touchRef(CV cv, AtsConnection connection, String externalId, String hash) {
		var query = Query.query(Criteria.where("_id").is(cv.getId()))
			.addCriteria(Criteria.where("atsRefs").elemMatch(
				Criteria.where("provider").is(connection.getProvider()).and("externalId").is(externalId)));
		mongoTemplate.updateFirst(query,
			new Update()
				.set("atsRefs.$.lastImportedAt", Instant.now())
				.set("atsRefs.$.resumeContentHash", hash),
			CV.class);
	}

	// ------------------------------------------------------------------ job bookkeeping

	private boolean isCancelled(String jobId) {
		var query = Query.query(Criteria.where("_id").is(jobId));
		query.fields().include("status");
		var current = mongoTemplate.findOne(query, BackgroundJob.class);
		return current == null || BackgroundJob.STATUS_CANCELLED.equals(current.getStatus());
	}

	private void heartbeat(String jobId, Counters counters) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(jobId)),
			new Update()
				.set("total", counters.total)
				.set("processed", counters.processed)
				.set("succeeded", counters.succeeded)
				.set("failed", counters.failed)
				.set("skipped", counters.skipped)
				.set("errorSamples", List.copyOf(counters.errorSamples))
				.set("leaseExpiresAt", Instant.now().plus(LEASE)),
			BackgroundJob.class);
	}

	private void finish(String jobId, String status, Counters counters, String failureReason) {
		var update = new Update()
			.set("status", status)
			.set("total", counters.total)
			.set("processed", counters.processed)
			.set("succeeded", counters.succeeded)
			.set("failed", counters.failed)
			.set("skipped", counters.skipped)
			.set("errorSamples", List.copyOf(counters.errorSamples))
			.set("finishedAt", Instant.now());
		if (failureReason != null) {
			update.set("failureReason", failureReason);
		}
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(jobId)), update, BackgroundJob.class);
	}

	private void recordSyncError(AtsConnection connection, String message) {
		var sync = connection.getSyncState() != null ? connection.getSyncState() : new AtsConnection.SyncState();
		sync.setLastSyncError(message);
		connection.setSyncState(sync);
		connectionRepository.save(connection);
	}

	private void sample(Counters counters, String message) {
		if (counters.errorSamples.size() < ERROR_SAMPLE_CAP) {
			counters.errorSamples.add(message);
		}
	}

	private String displayName(AtsCandidate candidate) {
		return StringUtils.hasText(candidate.name()) ? candidate.name() : String.valueOf(candidate.externalId());
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static final class Counters {
		long total;
		long processed;
		long succeeded;
		long failed;
		long skipped;
		final List<String> errorSamples = new ArrayList<>();
	}
}
