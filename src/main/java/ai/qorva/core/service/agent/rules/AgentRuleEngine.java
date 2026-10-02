package ai.qorva.core.service.agent.rules;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.agent.AgentExecutionScope;
import ai.qorva.core.service.agent.AgentPreconditionException;
import ai.qorva.core.service.agent.AgentRunService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One check of one claimed rule, as its owner: can it still run (owner, subscription, plan, target), is
 * anything new since its watermark, and if so — within its daily cap, one run at a time — record the records
 * in the ledger and queue a run for them.
 */
@Slf4j
@Component
public class AgentRuleEngine {

	private final AgentRuleStore store;
	private final Map<String, AgentTriggerSource> sources;
	private final AgentExecutionScope scope;
	private final AgentRunService runService;
	private final UsageMonitoringService usageMonitoringService;
	private final JobPostRepository jobPosts;
	private final AtsConnectionRepository connections;
	private final MongoTemplate mongoTemplate;
	private final AgentProperties properties;

	public AgentRuleEngine(AgentRuleStore store, List<AgentTriggerSource> sources, AgentExecutionScope scope,
	                       AgentRunService runService, UsageMonitoringService usageMonitoringService,
	                       JobPostRepository jobPosts, AtsConnectionRepository connections, MongoTemplate mongoTemplate,
	                       AgentProperties properties) {
		this.store = store;
		this.sources = sources.stream().collect(Collectors.toMap(AgentTriggerSource::type, Function.identity()));
		this.scope = scope;
		this.runService = runService;
		this.usageMonitoringService = usageMonitoringService;
		this.jobPosts = jobPosts;
		this.connections = connections;
		this.mongoTemplate = mongoTemplate;
		this.properties = properties;
	}

	/** Outcome of one check, for the tick log line. */
	public record Tick(int subjects, int runs, int skipped, String paused) {
		static final Tick NOTHING = new Tick(0, 0, 0, null);

		static Tick paused(String reason) {
			return new Tick(0, 0, 0, reason);
		}
	}

	public Tick check(AgentRule rule) {
		// The owner as the scope sees them today: exists, usable, still holds USE_AGENT, subscription not blocked.
		var owner = new AgentRun();
		owner.setTenantId(rule.getTenantId());
		owner.setUserEmail(rule.getOwnerEmail());
		owner.setLanguage(rule.getLanguage());
		owner.setOrigin(AgentRun.ORIGIN_RULE);
		try {
			return scope.call(owner, ctx -> checkAsOwner(rule, Instant.now()));
		} catch (AgentPreconditionException e) {
			var reason = QorvaErrorCodes.AUTH_SUBSCRIPTION_INACTIVE.equals(e.reason())
				? AgentRule.PAUSED_SUBSCRIPTION : AgentRule.PAUSED_OWNER_UNAVAILABLE;
			store.pause(rule, reason);
			return Tick.paused(reason);
		} catch (Exception e) {
			log.error("agent-rule {} check failed", rule.getId(), e);
			return Tick.NOTHING;
		}
	}

	private Tick checkAsOwner(AgentRule rule, Instant now) {
		var trigger = rule.getTrigger();
		if (trigger.getJobPostId() != null && jobPosts.findByIdInTenant(trigger.getJobPostId(), rule.getTenantId()).isEmpty()) {
			store.pause(rule, AgentRule.PAUSED_JOB_DELETED);
			return Tick.paused(AgentRule.PAUSED_JOB_DELETED);
		}
		if (trigger.getConnectionId() != null && connections.findByIdInTenant(trigger.getConnectionId(), rule.getTenantId()).isEmpty()) {
			store.pause(rule, AgentRule.PAUSED_CONNECTION_REMOVED);
			return Tick.paused(AgentRule.PAUSED_CONNECTION_REMOVED);
		}
		if (!usageMonitoringService.hasCapacityFor(rule.getTenantId(), UsageMonitoringService.FeatureKey.AGENT_RUNS, 1)) {
			store.pause(rule, AgentRule.PAUSED_QUOTA);
			return Tick.paused(AgentRule.PAUSED_QUOTA);
		}
		if (AgentRule.STATUS_PAUSED.equals(rule.getStatus())) {
			if (!store.resumeSelfPaused(rule)) return Tick.NOTHING;
			log.info("agent-rule {} resumed by itself", rule.getId());
		}

		var day = LocalDate.now(ZoneOffset.UTC).toString();
		if (!day.equals(rule.getCountersDay())) {
			rule.setCountersDay(day);
			rule.setRunsToday(0);
			rule.setSkippedToday(0);
		}
		if (hasActiveRun(rule)) {
			// One run at a time per rule: the records wait for the next tick.
			store.advance(rule);
			return Tick.NOTHING;
		}

		var source = sources.get(trigger.getType());
		if (source == null) return Tick.NOTHING;
		var since = rule.getWatermark() != null ? rule.getWatermark() : now;
		int limit = source.perRun(properties.getRules().getBatchSize());
		var subjects = source.newSubjects(rule, since, now, limit);
		if (AgentRule.TRIGGER_SCHEDULE.equals(trigger.getType()) && !subjects.isEmpty()) {
			rule.setNextRunAt(ScheduleSource.nextSlot(trigger, now));
		}
		if (subjects.isEmpty()) {
			store.advance(rule);
			return Tick.NOTHING;
		}
		var newest = subjects.stream().map(RuleSubject::at).max(Comparator.naturalOrder()).orElse(since);
		// Records sharing the watermark's instant are read again; the ledger says which already fired. If a
		// whole page of them shares one instant, step past it so the rule can't stall on it.
		var fired = store.firedKeys(rule, subjects.stream().map(RuleSubject::key).toList());
		var unseen = subjects.stream().filter(s -> !fired.contains(s.key())).toList();
		rule.setWatermark(unseen.isEmpty() && subjects.size() >= limit && newest.equals(since) ? newest.plus(1, ChronoUnit.MILLIS) : newest);
		if (unseen.isEmpty()) {
			store.advance(rule);
			return Tick.NOTHING;
		}

		if (rule.getRunsToday() >= rule.getDailyRunCap()) {
			// Over the cap: these records are skipped for good (counted), not fired later.
			rule.setSkippedToday(rule.getSkippedToday() + unseen.size());
			rule.setWatermark(newest.plusMillis(1));
			store.advance(rule);
			return new Tick(unseen.size(), 0, unseen.size(), null);
		}

		var runId = new ObjectId().toString();
		var fresh = new ArrayList<RuleSubject>();
		for (var subject : unseen) {
			// The unique ledger row is the guard: another instance may have just fired the same record.
			if (store.recordFiring(rule, subject.key(), runId, now)) fresh.add(subject);
		}
		if (fresh.isEmpty()) {
			store.advance(rule);
			return Tick.NOTHING;
		}

		var message = RuleRunMessage.of(rule, fresh);
		try {
			runService.startFromRule(rule, runId, message.goal(), message.message(), message.mentions());
		} catch (RuntimeException e) {
			log.error("agent-rule {} could not start its run; the records will fire on a later check", rule.getId(), e);
			store.forgetFirings(rule, runId);
			return Tick.NOTHING;
		}
		rule.setRunsToday(rule.getRunsToday() + 1);
		rule.setLastFiredAt(now);
		rule.setLastRunId(runId);
		store.advance(rule);
		return new Tick(fresh.size(), 1, 0, null);
	}

	/**
	 * A queued or running run of this rule. One waiting for approval doesn't count: it can wait for days, and the
	 * rule keeps working meanwhile (bounded by its daily cap).
	 */
	private boolean hasActiveRun(AgentRule rule) {
		return mongoTemplate.exists(Query.query(Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("ruleId").is(rule.getId()).and("status").in(AgentRun.STATUS_QUEUED, AgentRun.STATUS_RUNNING)), AgentRun.class);
	}
}
