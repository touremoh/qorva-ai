package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.scheduler.WorkerInstance;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

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

	public AgentRunStore(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/** Oldest QUEUED run, or a RUNNING one whose worker died. Cross-tenant by nature. */
	public AgentRun claimNext() {
		var now = Instant.now();
		var query = new Query(new Criteria().orOperator(
			Criteria.where("status").is(AgentRun.STATUS_QUEUED),
			Criteria.where("status").is(AgentRun.STATUS_RUNNING).and("leaseExpiresAt").lt(now)
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
	 * A queued run is cancelled at once; a running one is flagged and stops at its next step.
	 * Returns false when the run is not active (already finished).
	 */
	public boolean requestCancel(String tenantId, String runId) {
		var now = Instant.now();
		var byId = Criteria.where("_id").is(new ObjectId(runId)).and("tenantId").is(new ObjectId(tenantId));
		var queued = mongoTemplate.updateFirst(Query.query(new Criteria().andOperator(byId, Criteria.where("status").is(AgentRun.STATUS_QUEUED))),
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

	private static Update progress(AgentRun run) {
		return new Update()
			.set("status", run.getStatus())
			.set("steps", run.getSteps())
			.set("history", run.getHistory())
			.set("finalAnswer", run.getFinalAnswer())
			.set("failureReason", run.getFailureReason())
			.set("stoppedEarly", run.getStoppedEarly())
			.set("tokens", run.getTokens())
			.set("stepCount", run.getStepCount())
			.set("toolCallCount", run.getToolCallCount())
			.set("runningMillis", run.getRunningMillis())
			.set("metered", run.isMetered());
	}
}
