package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRuleFiring;
import ai.qorva.core.scheduler.WorkerInstance;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scheduler-side writes to agent_rules and the firing ledger. Field-level updates, so an owner editing or
 * pausing a rule and the scheduler moving its watermark don't overwrite each other's fields; the scheduler's
 * writes are conditioned on holding the rule's lease.
 */
@Component
public class AgentRuleStore {

	static final Duration LEASE = Duration.ofMinutes(5);

	private final MongoTemplate mongoTemplate;

	public AgentRuleStore(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/**
	 * Next rule due a check (active, or paused for a reason that lifts by itself), with a free lease. Its next
	 * check is pushed out by {@code interval}, so each rule is looked at once per tick across instances.
	 * Cross-tenant by nature.
	 */
	public AgentRule claimDue(Instant now, Duration interval) {
		var query = new Query(new Criteria().andOperator(
			new Criteria().orOperator(
				Criteria.where("status").is(AgentRule.STATUS_ACTIVE),
				Criteria.where("status").is(AgentRule.STATUS_PAUSED).and("pausedReason").in(AgentRule.SELF_RESUMING)),
			new Criteria().orOperator(Criteria.where("nextCheckAt").exists(false), Criteria.where("nextCheckAt").is(null),
				Criteria.where("nextCheckAt").lte(now)),
			new Criteria().orOperator(Criteria.where("leaseExpiresAt").exists(false), Criteria.where("leaseExpiresAt").is(null),
				Criteria.where("leaseExpiresAt").lt(now))
		)).with(Sort.by("nextCheckAt")).limit(1);
		var update = new Update()
			.set("leaseOwner", WorkerInstance.ID)
			.set("leaseExpiresAt", now.plus(LEASE))
			.set("nextCheckAt", now.plus(interval));
		return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), AgentRule.class);
	}

	public void release(AgentRule rule) {
		mongoTemplate.updateFirst(owned(rule), new Update().unset("leaseOwner").unset("leaseExpiresAt"), AgentRule.class);
	}

	/** Pauses an active rule (never overrides a pause someone chose). */
	public boolean pause(AgentRule rule, String reason) {
		var query = Query.query(byId(rule).and("status").is(AgentRule.STATUS_ACTIVE));
		var paused = mongoTemplate.updateFirst(query, new Update().set("status", AgentRule.STATUS_PAUSED)
			.set("pausedReason", reason).set("pausedAt", Instant.now()), AgentRule.class).getModifiedCount() == 1;
		if (!paused && AgentRule.SELF_RESUMING.contains(rule.getPausedReason()) && !reason.equals(rule.getPausedReason())) {
			// Already paused by itself, now for a different cause (e.g. quota → owner left): record the new one.
			paused = mongoTemplate.updateFirst(Query.query(byId(rule).and("status").is(AgentRule.STATUS_PAUSED)
					.and("pausedReason").in(AgentRule.SELF_RESUMING)),
				new Update().set("pausedReason", reason).set("pausedAt", Instant.now()), AgentRule.class).getModifiedCount() == 1;
		}
		if (paused) {
			rule.setStatus(AgentRule.STATUS_PAUSED);
			rule.setPausedReason(reason);
		}
		return paused;
	}

	/** Lifts a pause that lifts by itself (quota or subscription back). */
	public boolean resumeSelfPaused(AgentRule rule) {
		var query = Query.query(byId(rule).and("status").is(AgentRule.STATUS_PAUSED).and("pausedReason").in(AgentRule.SELF_RESUMING));
		var resumed = mongoTemplate.updateFirst(query, new Update().set("status", AgentRule.STATUS_ACTIVE)
			.unset("pausedReason").unset("pausedAt"), AgentRule.class).getModifiedCount() == 1;
		if (resumed) {
			rule.setStatus(AgentRule.STATUS_ACTIVE);
			rule.setPausedReason(null);
		}
		return resumed;
	}

	/** The tick's outcome: watermark, next slot and counters. Skipped when the lease was lost. */
	public boolean advance(AgentRule rule) {
		var update = new Update()
			.set("watermark", rule.getWatermark())
			.set("nextRunAt", rule.getNextRunAt())
			.set("countersDay", rule.getCountersDay())
			.set("runsToday", rule.getRunsToday())
			.set("skippedToday", rule.getSkippedToday())
			.set("lastFiredAt", rule.getLastFiredAt())
			.set("lastRunId", rule.getLastRunId());
		return mongoTemplate.updateFirst(owned(rule), update, AgentRule.class).getMatchedCount() == 1;
	}

	/** Records that the rule fired for this record; false when it already had (another tick or instance). */
	public boolean recordFiring(AgentRule rule, String subjectKey, String runId, Instant now) {
		try {
			mongoTemplate.insert(new AgentRuleFiring(null, rule.getTenantId(), rule.getId(), subjectKey, runId, now));
			return true;
		} catch (DuplicateKeyException e) {
			return false;
		}
	}

	/** Which of these records the rule already fired for (re-read because they share the watermark's instant). */
	public Set<String> firedKeys(AgentRule rule, Collection<String> keys) {
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("ruleId").is(rule.getId()).and("subjectKey").in(keys));
		query.fields().include("subjectKey");
		return mongoTemplate.find(query, AgentRuleFiring.class).stream().map(AgentRuleFiring::getSubjectKey).collect(Collectors.toSet());
	}

	/** Undoes the ledger rows of a run that could not be started, so the records fire on a later tick. */
	public void forgetFirings(AgentRule rule, String runId) {
		mongoTemplate.remove(Query.query(Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("ruleId").is(rule.getId()).and("runId").is(runId)), AgentRuleFiring.class);
	}

	private static Criteria byId(AgentRule rule) {
		return Criteria.where("_id").is(new ObjectId(rule.getId())).and("tenantId").is(new ObjectId(rule.getTenantId()));
	}

	private static Query owned(AgentRule rule) {
		return Query.query(byId(rule).and("leaseOwner").is(WorkerInstance.ID));
	}
}
