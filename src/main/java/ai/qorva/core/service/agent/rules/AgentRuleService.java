package ai.qorva.core.service.agent.rules;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.repository.AgentRuleFiringRepository;
import ai.qorva.core.dao.repository.AgentRuleRepository;
import ai.qorva.core.dto.AgentData;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.enums.MatchingStaleReasonEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.ats.AtsConnectionService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Objects;

/**
 * Standing rules as their users see them. A rule belongs to its creator, who alone edits or deletes it;
 * users who manage users see the team's rules and may pause or resume them. Every lookup is tenant-scoped.
 */
@Slf4j
@Service
public class AgentRuleService {

	static final int MAX_NAME = 100;
	/** Pre-approved matching: most actions one matching may cost without asking, and the default when unset. */
	static final int MAX_AUTO_APPROVE_ACTIONS = 500;
	static final int DEFAULT_AUTO_APPROVE_ACTIONS = 50;
	/** Pre-approved profile-update requests: most candidates one request may cover without asking, and the default. */
	static final int MAX_AUTO_APPROVE_PROFILE_UPDATES = 25;
	static final int DEFAULT_AUTO_APPROVE_PROFILE_UPDATES = 10;
	/** Report verdicts (Matching_report_response_format.json). */
	public static final List<String> RECOMMENDATIONS = List.of("strong_interview", "interview", "may_be", "reject");
	static final int MAX_IDLE_DAYS = 90;
	public static final List<Integer> STALE_MONTHS = List.of(6, 12, 18, 24);

	private final AgentRuleRepository repository;
	private final AgentRuleFiringRepository firings;
	private final MongoTemplate mongoTemplate;
	private final AgentProperties properties;
	private final JobPostService jobPostService;
	private final AtsConnectionService connectionService;

	public AgentRuleService(AgentRuleRepository repository, AgentRuleFiringRepository firings, MongoTemplate mongoTemplate,
	                        AgentProperties properties, JobPostService jobPostService, AtsConnectionService connectionService) {
		this.repository = repository;
		this.firings = firings;
		this.mongoTemplate = mongoTemplate;
		this.properties = properties;
		this.jobPostService = jobPostService;
		this.connectionService = connectionService;
	}

	public boolean rulesEnabled() {
		return properties.isEnabled() && properties.getRules().isEnabled();
	}

	public List<AgentData.RuleView> list(String tenantId, String userEmail, boolean team) {
		var rules = team ? repository.findByTenantIdOrderByCreatedAtAsc(tenantId)
			: repository.findByTenantIdAndOwnerEmailOrderByCreatedAtAsc(tenantId, userEmail);
		return rules.stream().map(r -> view(r, userEmail, team)).toList();
	}

	public AgentData.RuleView get(String tenantId, String userEmail, boolean team, String id) throws QorvaException {
		return view(visible(tenantId, userEmail, team, id), userEmail, team);
	}

	/** Checks a rule without saving it: what a {@code propose_rule} card shows. */
	public AgentRule validate(String tenantId, String userEmail, String language, AgentData.RuleRequest request) throws QorvaException {
		var rule = new AgentRule();
		rule.setTenantId(tenantId);
		rule.setOwnerEmail(userEmail);
		rule.setLanguage(language);
		apply(rule, request);
		return rule;
	}

	public AgentData.RuleView create(String tenantId, String userEmail, String language, AgentData.RuleRequest request)
		throws QorvaException {
		assertEnabled();
		if (repository.countByTenantId(tenantId) >= properties.getRules().getMaxPerTenant()) {
			throw QorvaErrors.conflict(QorvaErrorCodes.AGENT_RULE_LIMIT_REACHED);
		}
		var rule = validate(tenantId, userEmail, language, request);
		rule.setStatus(AgentRule.STATUS_ACTIVE);
		startFresh(rule, Instant.now());
		var saved = repository.save(rule);
		log.info("agent-rule {} created by {} (tenant={} trigger={})", saved.getId(), userEmail, tenantId, rule.getTrigger().getType());
		return view(saved, userEmail, false);
	}

	public AgentData.RuleView update(String tenantId, String userEmail, String id, AgentData.RuleRequest request) throws QorvaException {
		assertEnabled();
		var rule = owned(tenantId, userEmail, id);
		var before = rule.getTrigger();
		apply(rule, request);
		if (!sameTrigger(before, rule.getTrigger())) {
			// Watching something else now: start from now, and forget what the old trigger fired for.
			startFresh(rule, Instant.now());
			firings.deleteByTenantIdAndRuleId(tenantId, rule.getId());
		}
		var saved = repository.save(rule);
		log.info("agent-rule {} updated by {}", id, userEmail);
		return view(saved, userEmail, false);
	}

	public void delete(String tenantId, String userEmail, String id) throws QorvaException {
		var rule = owned(tenantId, userEmail, id);
		firings.deleteByTenantIdAndRuleId(tenantId, rule.getId());
		repository.delete(rule);
		log.info("agent-rule {} deleted by {}", id, userEmail);
	}

	public AgentData.RuleView pause(String tenantId, String userEmail, boolean team, String id) throws QorvaException {
		var rule = visible(tenantId, userEmail, team, id);
		mongoTemplate.updateFirst(byId(rule), new Update().set("status", AgentRule.STATUS_PAUSED)
			.set("pausedReason", AgentRule.PAUSED_MANUAL).set("pausedAt", Instant.now()), AgentRule.class);
		log.info("agent-rule {} paused by {}", id, userEmail);
		return get(tenantId, userEmail, team, id);
	}

	/** Resumes from now: what happened while it was paused is not fired retroactively. */
	public AgentData.RuleView resume(String tenantId, String userEmail, boolean team, String id) throws QorvaException {
		assertEnabled();
		var rule = visible(tenantId, userEmail, team, id);
		// Whatever paused it must be fixed first; the scheduler re-checks owner and quota on its next tick.
		assertTarget(rule.getTrigger());
		var now = Instant.now();
		startFresh(rule, now);
		mongoTemplate.updateFirst(byId(rule), new Update().set("status", AgentRule.STATUS_ACTIVE)
			.unset("pausedReason").unset("pausedAt").set("watermark", rule.getWatermark()).set("nextRunAt", rule.getNextRunAt())
			.unset("nextCheckAt"), AgentRule.class);
		log.info("agent-rule {} resumed by {}", id, userEmail);
		return get(tenantId, userEmail, team, id);
	}

	// ---------------------------------------------------------------------------------------------

	private void assertEnabled() throws QorvaException {
		if (!rulesEnabled()) {
			throw QorvaErrors.of(QorvaErrorCodes.AGENT_RULES_DISABLED, HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	private static void startFresh(AgentRule rule, Instant now) {
		rule.setWatermark(now);
		rule.setNextRunAt(AgentRule.TRIGGER_SCHEDULE.equals(rule.getTrigger().getType())
			? ScheduleSource.nextSlot(rule.getTrigger(), now) : null);
	}

	/** Validates the request onto the rule (name, goal, cap, trigger with its job or connection resolved). */
	private void apply(AgentRule rule, AgentData.RuleRequest request) throws QorvaException {
		if (request == null) throw invalid();
		var name = trim(request.getName());
		var goal = trim(request.getGoalTemplate());
		if (name == null || name.length() > MAX_NAME || goal == null || goal.length() > properties.getMaxGoalLength()) {
			throw invalid();
		}
		var rules = properties.getRules();
		int cap = request.getDailyRunCap() != null ? request.getDailyRunCap() : rules.getDefaultDailyRunCap();
		if (cap < 1 || cap > rules.getMaxDailyRunCap()) throw invalid();
		var trigger = trigger(request.getTrigger());
		boolean autoApprove = Boolean.TRUE.equals(request.getAutoApproveMatching());
		Integer maxActions = null;
		if (autoApprove) {
			maxActions = request.getAutoApproveMaxActions() != null ? request.getAutoApproveMaxActions() : DEFAULT_AUTO_APPROVE_ACTIONS;
			if (maxActions < 1 || maxActions > MAX_AUTO_APPROVE_ACTIONS) throw invalid();
		}
		rule.setName(name);
		rule.setGoalTemplate(goal);
		rule.setDailyRunCap(cap);
		rule.setTrigger(trigger);
		boolean autoProfileUpdates = Boolean.TRUE.equals(request.getAutoApproveProfileUpdates());
		Integer maxProfileUpdates = null;
		if (autoProfileUpdates) {
			maxProfileUpdates = request.getAutoApproveProfileUpdatesMax() != null
				? request.getAutoApproveProfileUpdatesMax() : DEFAULT_AUTO_APPROVE_PROFILE_UPDATES;
			if (maxProfileUpdates < 1 || maxProfileUpdates > MAX_AUTO_APPROVE_PROFILE_UPDATES) throw invalid();
		}
		rule.setAutoApproveMatching(autoApprove ? Boolean.TRUE : null);
		rule.setAutoApproveMaxActions(maxActions);
		rule.setAutoApproveProfileUpdates(autoProfileUpdates ? Boolean.TRUE : null);
		rule.setAutoApproveProfileUpdatesMax(maxProfileUpdates);
	}

	private AgentRule.Trigger trigger(AgentData.TriggerRequest request) throws QorvaException {
		if (request == null || request.getType() == null || !AgentRule.TRIGGERS.contains(request.getType())) throw invalid();
		var trigger = new AgentRule.Trigger();
		trigger.setType(request.getType());
		switch (request.getType()) {
			case AgentRule.TRIGGER_CV_ADDED, AgentRule.TRIGGER_DUPLICATE_FOUND -> trigger.setSource(source(request.getSource()));
			case AgentRule.TRIGGER_CV_SCORED -> {
				var min = request.getMinScore();
				var max = request.getMaxScore();
				if ((min != null && (min < 0 || min > 100)) || (max != null && (max < 0 || max > 100))
					|| (min != null && max != null && min > max)) {
					throw invalid();
				}
				trigger.setMinScore(min);
				trigger.setMaxScore(max);
				var verdicts = request.getRecommendations() == null ? List.<String>of()
					: request.getRecommendations().stream().filter(Objects::nonNull).map(v -> v.strip().toLowerCase(Locale.ROOT)).distinct().toList();
				if (!RECOMMENDATIONS.containsAll(verdicts)) throw invalid();
				if (!verdicts.isEmpty()) {
					// Every verdict picked is the same as none: store null, "any verdict".
					trigger.setRecommendations(verdicts.containsAll(RECOMMENDATIONS) ? null : verdicts);
				} else {
					trigger.setRecommendedOnly(Boolean.TRUE.equals(request.getRecommendedOnly()) ? Boolean.TRUE : null);
				}
				openJob(trigger, request.getJobPostId());
			}
			case AgentRule.TRIGGER_SCHEDULE -> {
				var frequency = request.getFrequency() == null ? AgentRule.Trigger.DAILY : request.getFrequency();
				if (!List.of(AgentRule.Trigger.DAILY, AgentRule.Trigger.WEEKLY).contains(frequency)
					|| request.getHour() == null || request.getHour() < 0 || request.getHour() > 23) {
					throw invalid();
				}
				boolean weekly = AgentRule.Trigger.WEEKLY.equals(frequency);
				if (weekly && (request.getWeekday() == null || request.getWeekday() < 1 || request.getWeekday() > 7)) throw invalid();
				trigger.setFrequency(frequency);
				trigger.setHour(request.getHour());
				trigger.setWeekday(weekly ? request.getWeekday() : null);
				trigger.setZoneId(zone(request.getZoneId()));
			}
			case AgentRule.TRIGGER_ATS_SYNC_FINISHED -> {
				var connectionId = trim(request.getConnectionId());
				if (connectionId != null) {
					var connection = connectionService.findOwned(currentTenant(), validId(connectionId));
					trigger.setConnectionId(connection.getId());
					trigger.setConnectionName(connection.getDisplayName() != null && !connection.getDisplayName().isBlank()
						? connection.getDisplayName() : connection.getProvider());
				}
			}
			case AgentRule.TRIGGER_JOB_NEEDS_MATCHING -> {
				var reasons = request.getStaleReasons() == null ? List.<String>of()
					: request.getStaleReasons().stream().filter(Objects::nonNull).map(String::strip).distinct().toList();
				var known = Arrays.stream(MatchingStaleReasonEnum.values()).map(Enum::name).toList();
				if (!known.containsAll(reasons)) throw invalid();
				// Every reason picked is the same as none picked: store null, "all of them".
				trigger.setStaleReasons(reasons.isEmpty() || reasons.containsAll(known) ? null : reasons);
				openJob(trigger, request.getJobPostId());
			}
			case AgentRule.TRIGGER_REPORT_STATUS_CHANGED -> {
				var statuses = statuses(request.getToStatuses());
				var known = Arrays.stream(ApplicationStatusEnum.values()).map(ApplicationStatusEnum::getStatus).toList();
				trigger.setToStatuses(statuses.isEmpty() || statuses.containsAll(known) ? null : statuses);
				anyJob(trigger, request.getJobPostId());
			}
			case AgentRule.TRIGGER_REPORT_STATUS_IDLE -> {
				// The statuses watched must be named: "idle in any status" would sweep the whole pipeline.
				var statuses = statuses(request.getToStatuses());
				var days = request.getIdleDays();
				if (statuses.isEmpty() || days == null || days < 1 || days > MAX_IDLE_DAYS) throw invalid();
				trigger.setToStatuses(statuses);
				trigger.setIdleDays(days);
				anyJob(trigger, request.getJobPostId());
			}
			case AgentRule.TRIGGER_CV_OUTDATED -> {
				var months = request.getStaleMonths() == null ? 18 : request.getStaleMonths();
				if (!STALE_MONTHS.contains(months)) throw invalid();
				trigger.setStaleMonths(months);
				trigger.setSource(source(request.getSource()));
			}
			case AgentRule.TRIGGER_JOB_CLOSED -> anyJob(trigger, request.getJobPostId());
			case AgentRule.TRIGGER_CANDIDATE_PROFILE_UPDATED -> {
				// Nothing to choose: every completed profile update.
			}
			default -> throw invalid();
		}
		return trigger;
	}

	private static String source(String requested) throws QorvaException {
		var source = requested == null ? AgentRule.Trigger.SOURCE_ANY : requested;
		if (!List.of(AgentRule.Trigger.SOURCE_ANY, AgentRule.Trigger.SOURCE_ATS, AgentRule.Trigger.SOURCE_MANUAL).contains(source)) {
			throw invalid();
		}
		return source;
	}

	private static List<String> statuses(List<String> requested) throws QorvaException {
		var statuses = requested == null ? List.<String>of()
			: requested.stream().filter(Objects::nonNull).map(s -> s.strip().toUpperCase(Locale.ROOT)).distinct().toList();
		var known = Arrays.stream(ApplicationStatusEnum.values()).map(ApplicationStatusEnum::getStatus).toList();
		if (!known.containsAll(statuses)) throw invalid();
		return statuses;
	}

	/** One open job, when given (matching only ever runs on open jobs). */
	private void openJob(AgentRule.Trigger trigger, String requestedJobId) throws QorvaException {
		var jobId = trim(requestedJobId);
		if (jobId == null) return;
		var job = jobPostService.findOneById(validId(jobId));
		if (!JobPostStatusEnum.OPEN.getStatus().equals(job.getStatus())) throw invalid();
		trigger.setJobPostId(job.getId());
		trigger.setJobTitle(job.getTitle());
	}

	/** One job, open or closed, when given. */
	private void anyJob(AgentRule.Trigger trigger, String requestedJobId) throws QorvaException {
		var jobId = trim(requestedJobId);
		if (jobId == null) return;
		var job = jobPostService.findOneById(validId(jobId));
		trigger.setJobPostId(job.getId());
		trigger.setJobTitle(job.getTitle());
	}

	/** The job or connection a paused rule points to must exist again before it can resume. */
	private void assertTarget(AgentRule.Trigger trigger) throws QorvaException {
		if (trigger.getJobPostId() != null) {
			jobPostService.findOneById(trigger.getJobPostId());
		}
		if (trigger.getConnectionId() != null) {
			connectionService.findOwned(currentTenant(), trigger.getConnectionId());
		}
	}

	private static String currentTenant() {
		return TenantContextHolder.getTenantId();
	}

	private static String zone(String zoneId) throws QorvaException {
		if (zoneId == null || zoneId.isBlank()) return "UTC";
		try {
			return ZoneId.of(zoneId.strip()).getId();
		} catch (DateTimeException e) {
			throw invalid();
		}
	}

	private static String validId(String id) throws QorvaException {
		if (!ObjectId.isValid(id)) throw invalid();
		return id;
	}

	/** Whether an edit kept the rule watching the same thing; any watched field counts (names and titles do not). */
	static boolean sameTrigger(AgentRule.Trigger a, AgentRule.Trigger b) {
		return a != null && b != null && Objects.equals(a.getType(), b.getType()) && Objects.equals(a.getSource(), b.getSource())
			&& Objects.equals(a.getJobPostId(), b.getJobPostId()) && Objects.equals(a.getMinScore(), b.getMinScore())
			&& Objects.equals(a.getMaxScore(), b.getMaxScore()) && Objects.equals(a.getRecommendedOnly(), b.getRecommendedOnly())
			&& Objects.equals(a.getRecommendations(), b.getRecommendations()) && Objects.equals(a.getFrequency(), b.getFrequency())
			&& Objects.equals(a.getHour(), b.getHour()) && Objects.equals(a.getWeekday(), b.getWeekday())
			&& Objects.equals(a.getZoneId(), b.getZoneId()) && Objects.equals(a.getConnectionId(), b.getConnectionId())
			&& Objects.equals(a.getStaleReasons(), b.getStaleReasons()) && Objects.equals(a.getToStatuses(), b.getToStatuses())
			&& Objects.equals(a.getIdleDays(), b.getIdleDays()) && Objects.equals(a.getStaleMonths(), b.getStaleMonths());
	}

	private AgentRule visible(String tenantId, String userEmail, boolean team, String id) throws QorvaException {
		if (id == null || !ObjectId.isValid(id)) throw notFound();
		return repository.findByIdInTenant(id, tenantId)
			.filter(r -> team || userEmail.equals(r.getOwnerEmail()))
			.orElseThrow(AgentRuleService::notFound);
	}

	private AgentRule owned(String tenantId, String userEmail, String id) throws QorvaException {
		return visible(tenantId, userEmail, false, id);
	}

	private static Query byId(AgentRule rule) {
		return Query.query(Criteria.where("_id").is(new ObjectId(rule.getId())).and("tenantId").is(new ObjectId(rule.getTenantId())));
	}

	private static String trim(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static QorvaException invalid() {
		return QorvaErrors.badRequest(QorvaErrorCodes.AGENT_RULE_INVALID);
	}

	private static QorvaException notFound() {
		return QorvaErrors.notFound(QorvaErrorCodes.AGENT_RULE_NOT_FOUND);
	}

	public static AgentData.TriggerView triggerView(AgentRule.Trigger t) {
		return new AgentData.TriggerView(t.getType(), t.getSource(), t.getJobPostId(), t.getJobTitle(), t.getMinScore(),
			t.getRecommendedOnly(), t.getFrequency(), t.getHour(), t.getWeekday(), t.getZoneId(), t.getConnectionId(),
			t.getConnectionName(), t.getStaleReasons(), t.getToStatuses(), t.getMaxScore(), t.getRecommendations(), t.getIdleDays(),
			t.getStaleMonths());
	}

	static AgentData.RuleView view(AgentRule rule, String userEmail, boolean team) {
		boolean mine = userEmail.equals(rule.getOwnerEmail());
		var today = LocalDate.now(ZoneOffset.UTC).toString();
		boolean countsToday = today.equals(rule.getCountersDay());
		return new AgentData.RuleView(rule.getId(), rule.getName(), rule.getOwnerEmail(), triggerView(rule.getTrigger()),
			rule.getGoalTemplate(), rule.getDailyRunCap(), Boolean.TRUE.equals(rule.getAutoApproveMatching()),
			rule.getAutoApproveMaxActions(), Boolean.TRUE.equals(rule.getAutoApproveProfileUpdates()),
			rule.getAutoApproveProfileUpdatesMax(), rule.getStatus(), rule.getPausedReason(),
			countsToday ? rule.getRunsToday() : 0, countsToday ? rule.getSkippedToday() : 0, rule.getLastRunId(),
			rule.getLastFiredAt(), rule.getNextRunAt(), rule.getCreatedAt(), mine, mine || team);
	}
}
