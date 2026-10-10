package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.scheduler.WorkerInstance;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import ai.qorva.core.service.TenantAccess;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Every write to agent_runs outside creation. Field-level updates, never a whole-document save, so
 * the API's cancel flag and the worker's progress can't overwrite each other; worker writes are
 * conditioned on still holding the lease, so an instance that lost it stops instead of clobbering.
 */
@Component
public class AgentRunStore {

	/** Longer than one model call with retries (3 × 60 s), renewed after every step. */
	static final Duration LEASE = Duration.ofMinutes(5);

	private final MongoTemplate mongoTemplate;
	private final TenantAccess tenantAccess;

	public AgentRunStore(MongoTemplate mongoTemplate, TenantAccess tenantAccess) {
		this.tenantAccess = tenantAccess;
		this.mongoTemplate = mongoTemplate;
	}

	/** Oldest QUEUED run, or a RUNNING one whose worker died. Cross-tenant by nature. */
	public AgentRun claimNext() {
		var now = Instant.now();
		var query = new Query(new Criteria().andOperator(
			new Criteria().orOperator(
				Criteria.where("status").is(AgentRun.STATUS_QUEUED),
				Criteria.where("status").is(AgentRun.STATUS_RUNNING).and("leaseExpiresAt").lt(now)),
			// Runs of a suspended, deleted or expired company wait until it is usable again.
			tenantAccess.usableTenantsOnly("tenantId")
		)).with(Sort.by("createdAt")).limit(1);
		var update = new Update()
			.set("status", AgentRun.STATUS_RUNNING)
			.set("leaseOwner", WorkerInstance.ID)
			.set("leaseExpiresAt", now.plus(LEASE))
			.set("lastUpdatedAt", now);
		var claimed = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), AgentRun.class);
		if (claimed != null && claimed.getStartedAt() == null) {
			mongoTemplate.updateFirst(owned(claimed), Update.update("startedAt", now), AgentRun.class);
			claimed.setStartedAt(now);
		}
		return claimed;
	}

	/** Persists the worker's progress and renews the lease. False: the lease was lost, stop working on it. */
	public boolean saveProgress(AgentRun run) {
		var now = Instant.now();
		var update = progress(run)
			.set("leaseExpiresAt", now.plus(LEASE))
			.set("lastUpdatedAt", now);
		return mongoTemplate.updateFirst(owned(run), update, AgentRun.class).getMatchedCount() == 1;
	}

	/**
	 * The run waits for the user: progress and pending actions saved, lease released so no worker holds it while
	 * nobody is deciding. Only {@link #decide} brings it back to QUEUED.
	 */
	public boolean pause(AgentRun run) {
		var now = Instant.now();
		var update = progress(run)
			.unset("leaseOwner")
			.unset("leaseExpiresAt")
			// A new pause of a rule run is news for its owner's digest email.
			.unset("notifiedAt")
			.set("lastUpdatedAt", now);
		return mongoTemplate.updateFirst(owned(run), update, AgentRun.class).getMatchedCount() == 1;
	}

	public enum Decision { APPROVED, REJECTED }

	/**
	 * Records the user's decision on one pending action, only if the run is still waiting, the action is still
	 * pending and its arguments are the ones the user saw. When no action is left pending the run is re-queued.
	 * Returns false when the action is stale (already decided, changed, expired or cancelled).
	 */
	public boolean decide(String tenantId, String runId, String actionId, String argsHash, Decision decision,
	                      String editedSubject, String editedBody, String reason) {
		var now = Instant.now();
		var action = Criteria.where("actionId").is(actionId).and("argsHash").is(argsHash).and("status").is(AgentRun.PendingAction.PENDING);
		var query = Query.query(Criteria.where("_id").is(new ObjectId(runId)).and("tenantId").is(new ObjectId(tenantId))
			.and("status").is(AgentRun.STATUS_AWAITING_APPROVAL).and("pendingActions").elemMatch(action));
		var update = new Update()
			.set("pendingActions.$.status", decision.name())
			.set("pendingActions.$.decidedAt", now)
			.set("lastUpdatedAt", now);
		if (decision == Decision.APPROVED) {
			update.set("pendingActions.$.editedSubject", editedSubject).set("pendingActions.$.editedBody", editedBody);
		} else {
			update.set("pendingActions.$.reason", reason);
		}
		if (mongoTemplate.updateFirst(query, update, AgentRun.class).getModifiedCount() != 1) {
			return false;
		}
		// Idempotent: concurrent last decisions may both get here; the run is queued once either way.
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(runId)).and("tenantId").is(new ObjectId(tenantId))
				.and("status").is(AgentRun.STATUS_AWAITING_APPROVAL)
				.and("pendingActions").not().elemMatch(Criteria.where("status").is(AgentRun.PendingAction.PENDING))),
			new Update().set("status", AgentRun.STATUS_QUEUED).set("lastUpdatedAt", now), AgentRun.class);
		return true;
	}

	/** Runs whose approval cards went unanswered past their deadline end as EXPIRED. Cross-tenant sweep. */
	public long expireApprovals() {
		var now = Instant.now();
		var result = mongoTemplate.updateMulti(
			Query.query(Criteria.where("status").is(AgentRun.STATUS_AWAITING_APPROVAL).and("approvalExpiresAt").lt(now)),
			new Update().set("status", AgentRun.STATUS_EXPIRED).set("failureReason", QorvaErrorCodes.AGENT_APPROVAL_EXPIRED)
				.set("finishedAt", now).set("lastUpdatedAt", now),
			AgentRun.class);
		return result.getModifiedCount();
	}

	/** Final write: terminal status, lease released. */
	public boolean finish(AgentRun run) {
		var now = Instant.now();
		run.setFinishedAt(now);
		var update = progress(run)
			.set("finishedAt", now)
			.unset("leaseOwner")
			.unset("leaseExpiresAt")
			.set("lastUpdatedAt", now);
		return mongoTemplate.updateFirst(owned(run), update, AgentRun.class).getMatchedCount() == 1;
	}

	public boolean isCancelRequested(AgentRun run) {
		var query = Query.query(Criteria.where("_id").is(new ObjectId(run.getId())).and("tenantId").is(new ObjectId(run.getTenantId())));
		query.fields().include("cancelRequested");
		var found = mongoTemplate.findOne(query, AgentRun.class);
		return found == null || found.isCancelRequested();
	}

	/**
	 * A queued run, or one waiting for approval (no worker holds it), is cancelled at once; a running one is
	 * flagged and stops at its next step.
	 * Returns false when the run is not active (already finished).
	 */
	public boolean requestCancel(String tenantId, String runId) {
		var now = Instant.now();
		var byId = Criteria.where("_id").is(new ObjectId(runId)).and("tenantId").is(new ObjectId(tenantId));
		var queued = mongoTemplate.updateFirst(Query.query(new Criteria().andOperator(byId, Criteria.where("status").in(AgentRun.STATUS_QUEUED, AgentRun.STATUS_AWAITING_APPROVAL))),
			new Update().set("status", AgentRun.STATUS_CANCELLED).set("cancelRequested", true)
				.set("finishedAt", now).set("lastUpdatedAt", now), AgentRun.class);
		if (queued.getModifiedCount() == 1) return true;
		var active = mongoTemplate.updateFirst(Query.query(new Criteria().andOperator(
				Criteria.where("_id").is(new ObjectId(runId)).and("tenantId").is(new ObjectId(tenantId)),
				Criteria.where("status").in(AgentRun.ACTIVE_STATUSES))),
			new Update().set("cancelRequested", true).set("lastUpdatedAt", now), AgentRun.class);
		return active.getMatchedCount() == 1;
	}

	private static Query owned(AgentRun run) {
		return Query.query(Criteria.where("_id").is(new ObjectId(run.getId()))
			.and("tenantId").is(new ObjectId(run.getTenantId()))
			.and("leaseOwner").is(WorkerInstance.ID));
	}

	/** A run as stored, within its tenant. */
	public Optional<AgentRun> find(String tenantId, String runId) {
		if (!ObjectId.isValid(tenantId) || runId == null) return Optional.empty();
		return Optional.ofNullable(mongoTemplate.findOne(Query.query(Criteria.where("_id").is(runId)
			.and("tenantId").is(new ObjectId(tenantId))), AgentRun.class));
	}

	/** The completed runs of the same conversation (same tenant and user) created before {@code run}, oldest first. */
	public List<AgentRun> earlierInConversation(AgentRun run) {
		if (run.getConversationId() == null || run.getCreatedAt() == null) return List.of();
		return mongoTemplate.find(Query.query(Criteria.where("tenantId").is(new ObjectId(run.getTenantId()))
				.and("userEmail").is(run.getUserEmail())
				.and("conversationId").is(run.getConversationId())
				.and("status").is(AgentRun.STATUS_COMPLETED)
				.and("createdAt").lt(run.getCreatedAt()))
			.with(Sort.by(Sort.Direction.ASC, "createdAt")), AgentRun.class);
	}

	private static Update progress(AgentRun run) {
		return new Update()
			.set("status", run.getStatus())
			.set("steps", run.getSteps())
			.set("history", run.getHistory())
			.set("finalAnswer", run.getFinalAnswer())
			.set("blocks", run.getBlocks())
			.set("insightFrame", run.getInsightFrame())
			.set("failureReason", run.getFailureReason())
			.set("stoppedEarly", run.getStoppedEarly())
			.set("tokens", run.getTokens())
			.set("stepCount", run.getStepCount())
			.set("toolCallCount", run.getToolCallCount())
			.set("runningMillis", run.getRunningMillis())
			.set("metered", run.isMetered())
			.set("pendingActions", run.getPendingActions())
			.set("pendingToolResults", run.getPendingToolResults())
			.set("approvalExpiresAt", run.getApprovalExpiresAt())
			.set("outboundCount", run.getOutboundCount());
	}
}
