package ai.qorva.core.service.ats;

import ai.qorva.core.security.TenantScope;

import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.entity.AtsOutboundTask;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.dao.repository.AtsOutboundTaskRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.service.ats.AtsModels.MatchWriteBack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Queued push of matching scores back to the source ATS. Enqueued when a report lands
 * for an ATS-linked CV (AIScreeningService), drained here with retry/backoff. Tasks
 * are claimed with an atomic findAndModify so parallel instances never double-post.
 * A provider outage only ever delays notes — report generation is untouched.
 */
@Slf4j
@Service
public class AtsWriteBackService {

	private static final int DRAIN_BATCH = 20;
	private static final long BASE_RETRY_MINUTES = 5;

	private final AtsOutboundTaskRepository taskRepository;
	private final AtsConnectionRepository connectionRepository;
	private final AtsConnectionService connectionService;
	private final AtsConnectorRegistry registry;
	private final AtsOauthService oauthService;
	private final MongoTemplate mongoTemplate;
	private final String appBaseUrl;

	public AtsWriteBackService(
		AtsOutboundTaskRepository taskRepository,
		AtsConnectionRepository connectionRepository,
		AtsConnectionService connectionService,
		AtsConnectorRegistry registry,
		AtsOauthService oauthService,
		MongoTemplate mongoTemplate,
		@Value("${weblink.appBaseUrl:}") String appBaseUrl
	) {
		this.taskRepository = taskRepository;
		this.connectionRepository = connectionRepository;
		this.connectionService = connectionService;
		this.registry = registry;
		this.oauthService = oauthService;
		this.mongoTemplate = mongoTemplate;
		this.appBaseUrl = appBaseUrl;
	}

	/** Where the note's "Full report" link points — the app has no per-report route. */
	private String reportsUrl() {
		if (appBaseUrl == null || appBaseUrl.isBlank()) {
			return null;
		}
		var base = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
		return base + "/app/reports";
	}

	/** Best-effort enqueue — never throws into the screening pipeline. */
	public void maybeEnqueue(CVDTO cv, JobPostDTO jobPost, MatchingReportDetails details) {
		try {
			if (cv.getAtsRefs() == null || cv.getAtsRefs().isEmpty()) {
				return;
			}
			Double score = details.getDecisionSummary() != null ? details.getDecisionSummary().getFinalScore() : null;
			String headline = details.getDecisionSummary() != null ? details.getDecisionSummary().getReportHeadline() : null;
			for (var ref : cv.getAtsRefs()) {
				var connection = connectionRepository.findByIdInTenant(ref.getConnectionId(), cv.getTenantId()).orElse(null);
				if (connection == null
					|| !AtsConnection.STATUS_CONNECTED.equals(connection.getStatus())
					|| connection.getSettings() == null
					|| !Boolean.TRUE.equals(connection.getSettings().getWriteBackScores())) {
					continue;
				}
				taskRepository.save(AtsOutboundTask.builder()
					.tenantId(cv.getTenantId())
					.connectionId(connection.getId())
					.provider(connection.getProvider())
					.cvId(cv.getId())
					.externalCandidateId(ref.getExternalId())
					.externalApplicationId(ref.getExternalApplicationId())
					.jobTitle(jobPost.getTitle())
					.score(score)
					.headline(headline)
					.reportUrl(reportsUrl())
					.status(AtsOutboundTask.STATUS_PENDING)
					.attempts(0)
					.nextAttemptAt(Instant.now())
					.createdAt(Instant.now())
					.build());
			}
		} catch (Exception e) {
			log.warn("Could not enqueue ATS write-back for CV {}: {}", cv.getId(), e.getMessage());
		}
	}

	@Scheduled(fixedDelayString = "${qorva.ats.outbound-poll-delay-ms:60000}")
	public void drain() {
		var now = Instant.now();
		// SENDING is included so a task whose sender died mid-flight is picked up again once
		// its claim lease expires — otherwise it would sit in SENDING forever, never retried.
		var due = taskRepository.findByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
			List.of(AtsOutboundTask.STATUS_PENDING, AtsOutboundTask.STATUS_SENDING),
			now, PageRequest.of(0, DRAIN_BATCH));
		for (var candidate : due) {
			var claimed = claim(candidate.getId());
			if (claimed != null) {
				TenantScope.runAs(claimed.getTenantId(), () -> send(claimed));
			}
		}
	}

	/**
	 * Atomic claim of a due task; losers of the race get null. The claim pushes
	 * nextAttemptAt out by the lease, so an in-flight task stays invisible to other drains
	 * until it either finishes or the lease expires. attempts still caps total tries, so a
	 * task that keeps killing its sender ends up FAILED rather than looping forever.
	 */
	private AtsOutboundTask claim(String taskId) {
		var now = Instant.now();
		return mongoTemplate.findAndModify(
			Query.query(Criteria.where("_id").is(taskId)
				.and("status").in(AtsOutboundTask.STATUS_PENDING, AtsOutboundTask.STATUS_SENDING)
				.and("nextAttemptAt").lte(now)),
			new Update()
				.set("status", AtsOutboundTask.STATUS_SENDING)
				.set("nextAttemptAt", now.plusSeconds(AtsOutboundTask.CLAIM_LEASE_MINUTES * 60))
				.inc("attempts", 1),
			FindAndModifyOptions.options().returnNew(true),
			AtsOutboundTask.class);
	}

	private void send(AtsOutboundTask task) {
		try {
			var connection = connectionRepository.findByIdInTenant(task.getConnectionId(), task.getTenantId()).orElse(null);
			if (connection == null) {
				markFailed(task, "connection_deleted");
				return;
			}
			var provider = AtsProviderEnum.fromValue(connection.getProvider());
			var credentials = oauthService.ensureFreshToken(connection, connectionService.decryptCredentials(connection));
			registry.get(provider).pushMatchResult(credentials, new MatchWriteBack(
				task.getExternalCandidateId(),
				task.getExternalApplicationId(),
				task.getJobTitle(),
				task.getScore(),
				task.getHeadline(),
				task.getReportUrl()));
			mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(task.getId())),
				new Update().set("status", AtsOutboundTask.STATUS_SENT).set("sentAt", Instant.now()),
				AtsOutboundTask.class);
		} catch (Exception e) {
			if (task.getAttempts() >= AtsOutboundTask.MAX_ATTEMPTS) {
				markFailed(task, e.getMessage());
			} else {
				long delayMinutes = BASE_RETRY_MINUTES * (1L << Math.min(task.getAttempts(), 6));
				mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(task.getId())),
					new Update()
						.set("status", AtsOutboundTask.STATUS_PENDING)
						.set("lastError", e.getMessage())
						.set("nextAttemptAt", Instant.now().plusSeconds(delayMinutes * 60)),
					AtsOutboundTask.class);
			}
			log.warn("ATS write-back attempt {} failed for task {}: {}", task.getAttempts(), task.getId(), e.getMessage());
		}
	}

	private void markFailed(AtsOutboundTask task, String error) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(task.getId())),
			new Update().set("status", AtsOutboundTask.STATUS_FAILED).set("lastError", error),
			AtsOutboundTask.class);
	}
}
