package ai.qorva.core.service.agent;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.repository.AgentRunRepository;
import ai.qorva.core.dto.AgentData;
import ai.qorva.core.dto.common.UsageFeatureMetrics;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.scheduler.AgentRunWorker;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.UsageMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Starting, reading and cancelling Copilot runs. Every query carries the tenant; a run is visible
 * to its user, and — read-only plus cancel — to users who can manage users ({@code teamView}).
 */
@Slf4j
@Service
public class AgentRunService {

	/** Previous exchanges of a conversation replayed to the model, so follow-ups ("tag them") have context. */
	static final int CONTEXT_RUNS = 3;
	/** The meters a chat run can be charged to. */
	static final List<UsageMonitoringService.FeatureKey> ANSWER_METERS = List.of(UsageMonitoringService.FeatureKey.AGENT_RUNS,
		UsageMonitoringService.FeatureKey.AI_RESUME_CHATS, UsageMonitoringService.FeatureKey.TALENT_INTELLIGENCE_QUERIES);
	static final int TITLE_LENGTH = 80;
	static final int MAX_SUBJECT = 200;
	static final int MAX_BODY = 8000;
	static final int MAX_REASON = 500;
	private static final int CONVERSATION_SCAN = 500;

	private final AgentRunRepository repository;
	private final MongoTemplate mongoTemplate;
	private final AgentRunStore store;
	private final AgentProperties properties;
	private final UsageMonitoringService usageMonitoringService;
	private final CVService cvService;
	private final JobPostService jobPostService;
	private final AgentRunWorker worker;

	public AgentRunService(AgentRunRepository repository, MongoTemplate mongoTemplate, AgentRunStore store,
	                       AgentProperties properties, UsageMonitoringService usageMonitoringService,
	                       CVService cvService, JobPostService jobPostService, AgentRunWorker worker) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
		this.store = store;
		this.properties = properties;
		this.usageMonitoringService = usageMonitoringService;
		this.cvService = cvService;
		this.jobPostService = jobPostService;
		this.worker = worker;
	}

	public AgentData.Availability availability(String tenantId, boolean teamView) {
		var features = usageMonitoringService.findCurrentPeriodByTenantId(tenantId).map(p -> p.getFeatures()).orElse(null);
		return new AgentData.Availability(properties.isEnabled(),
			properties.isEnabled() && properties.getRules().isEnabled(),
			remaining(features == null ? null : features.getAgentRuns()),
			remaining(features == null ? null : features.getAiResumeChats()),
			remaining(features == null ? null : features.getTalentIntelligenceQueries()), teamView);
	}

	private static Integer remaining(UsageFeatureMetrics metrics) {
		if (metrics == null || metrics.getLimit() == null) return null;
		return Math.max(0, metrics.getLimit() - (metrics.getConsumed() != null ? metrics.getConsumed() : 0));
	}

	public AgentData.RunView start(String tenantId, String userEmail, String language, AgentData.StartRunRequest request)
		throws QorvaException {
		if (!properties.isEnabled()) {
			throw QorvaErrors.of(QorvaErrorCodes.AGENT_DISABLED, HttpStatus.SERVICE_UNAVAILABLE);
		}
		var goal = request.getGoal() != null ? request.getGoal().strip() : "";
		if (goal.isEmpty() || goal.length() > properties.getMaxGoalLength()) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.AGENT_GOAL_INVALID);
		}
		// One chat task at a time; runs started by the user's rules don't block their chat.
		if (mongoTemplate.exists(Query.query(mine(tenantId, userEmail).and("origin").is(AgentRun.ORIGIN_CHAT)
			.and("status").in(AgentRun.ACTIVE_STATUSES)), AgentRun.class)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.AGENT_RUN_ACTIVE);
		}
		// A run is charged by what it uses (a task, a candidate question or a library analysis): it can start while any has room.
		if (ANSWER_METERS.stream().noneMatch(key -> usageMonitoringService.hasCapacityFor(tenantId, key, 1))) {
			throw QorvaErrors.forbidden(QorvaErrorCodes.USAGE_AGENT_LIMIT_EXCEEDED);
		}

		var mentions = resolveMentions(request.getMentions());
		var previous = request.getConversationId() == null ? List.<AgentRun>of() : conversationRuns(tenantId, userEmail, request.getConversationId());
		var focus = request.getFocus() != null ? resolveFocus(request.getFocus())
			: previous.isEmpty() ? null : previous.getLast().getFocus();

		var run = new AgentRun();
		run.setTenantId(tenantId);
		run.setUserEmail(userEmail);
		run.setLanguage(language);
		run.setOrigin(AgentRun.ORIGIN_CHAT);
		run.setTimeZone(validZone(request.getTimeZone()));
		run.setGoal(goal);
		run.setMentions(mentions);
		run.setFocus(focus);
		run.setStatus(AgentRun.STATUS_QUEUED);
		if (previous.isEmpty()) {
			run.setConversationId(UUID.randomUUID().toString());
			run.setTitle(title(goal));
		} else {
			run.setConversationId(request.getConversationId());
			run.setTitle(previous.getFirst().getTitle());
		}
		run.setHistory(initialHistory(previous, goal, mentions, focus));

		var saved = repository.save(run);
		log.info("agent-run {} queued (tenant={} conversation={})", saved.getId(), tenantId, saved.getConversationId());
		worker.wakeUp();
		return view(saved, true);
	}

	/**
	 * Queues a run for a standing rule, as its owner. The scheduler has checked the owner, the plan and the rule's
	 * caps, and recorded the records in the firing ledger under {@code runId}.
	 */
	public AgentRun startFromRule(AgentRule rule, String runId, String goal, String message, List<AgentRun.Mention> mentions) {
		var run = new AgentRun();
		run.setId(runId);
		run.setTenantId(rule.getTenantId());
		run.setUserEmail(rule.getOwnerEmail());
		run.setLanguage(rule.getLanguage());
		run.setOrigin(AgentRun.ORIGIN_RULE);
		run.setRuleId(rule.getId());
		run.setRuleName(rule.getName());
		// The pre-approval as it is now: editing the rule later never changes a run already started.
		run.setAutoApproveMaxActions(Boolean.TRUE.equals(rule.getAutoApproveMatching()) ? rule.getAutoApproveMaxActions() : null);
		run.setGoal(goal);
		run.setMentions(new ArrayList<>(mentions));
		run.setStatus(AgentRun.STATUS_QUEUED);
		run.setConversationId(UUID.randomUUID().toString());
		run.setTitle(title(rule.getName()));
		// A preset id makes Spring Data treat the run as existing, so the creation date is set here.
		run.setCreatedAt(Instant.now());
		run.setHistory(new ArrayList<>(List.of(AgentHistory.user(message))));
		var saved = repository.save(run);
		log.info("agent-run {} queued by rule {} (tenant={} mentions={})", saved.getId(), rule.getId(), rule.getTenantId(), mentions.size());
		worker.wakeUp();
		return saved;
	}

	public AgentData.RunView get(String tenantId, String userEmail, boolean teamView, String runId) throws QorvaException {
		var run = visible(tenantId, userEmail, teamView, runId);
		boolean mine = userEmail.equals(run.getUserEmail());
		// Admins viewing the team may cancel someone else's run, never approve for them.
		return view(run, mine || teamView, mine);
	}

	public AgentData.RunView cancel(String tenantId, String userEmail, boolean teamView, String runId) throws QorvaException {
		var run = visible(tenantId, userEmail, teamView, runId);
		if (store.requestCancel(tenantId, run.getId())) {
			log.info("agent-run {} cancel requested by {}", run.getId(), userEmail);
		}
		return view(visible(tenantId, userEmail, teamView, runId), userEmail.equals(run.getUserEmail()));
	}

	public AgentData.RunView approve(String tenantId, String userEmail, String runId, String actionId,
	                                 AgentData.DecisionRequest request) throws QorvaException {
		var run = visible(tenantId, userEmail, false, runId);
		var subject = trimToNull(request.getSubject());
		var body = trimToNull(request.getBody());
		if ((subject != null && subject.length() > MAX_SUBJECT) || (body != null && body.length() > MAX_BODY)
			|| (request.getSubject() != null && subject == null) || (request.getBody() != null && body == null)) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.AGENT_ACTION_INVALID);
		}
		if (!store.decide(tenantId, run.getId(), actionId, request.getArgsHash(), AgentRunStore.Decision.APPROVED, subject, body, null)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.AGENT_ACTION_STALE);
		}
		log.info("agent-run {} action {} approved by {}", run.getId(), actionId, userEmail);
		worker.wakeUp();
		return view(visible(tenantId, userEmail, false, runId), true);
	}

	public AgentData.RunView reject(String tenantId, String userEmail, String runId, String actionId,
	                                AgentData.DecisionRequest request) throws QorvaException {
		var run = visible(tenantId, userEmail, false, runId);
		var reason = trimToNull(request.getReason());
		if (reason != null && reason.length() > MAX_REASON) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.AGENT_ACTION_INVALID);
		}
		if (!store.decide(tenantId, run.getId(), actionId, request.getArgsHash(), AgentRunStore.Decision.REJECTED, null, null, reason)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.AGENT_ACTION_STALE);
		}
		log.info("agent-run {} action {} rejected by {}", run.getId(), actionId, userEmail);
		worker.wakeUp();
		return view(visible(tenantId, userEmail, false, runId), true);
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public AgentData.RunPage list(String tenantId, String userEmail, boolean team, String status, String origin,
	                              String ruleId, String user, int page, int size) {
		var criteria = team ? tenant(tenantId) : mine(tenantId, userEmail);
		if (team && user != null && !user.isBlank()) criteria = criteria.and("userEmail").is(user);
		if (status != null && !status.isBlank()) criteria = criteria.and("status").is(status);
		if (origin != null && !origin.isBlank()) criteria = criteria.and("origin").is(origin);
		if (ruleId != null && !ruleId.isBlank()) criteria = criteria.and("ruleId").is(ruleId);
		int safeSize = Math.max(1, Math.min(50, size));
		int safePage = Math.max(0, page);
		var query = Query.query(criteria);
		long total = mongoTemplate.count(query, AgentRun.class);
		query.with(Sort.by(Sort.Direction.DESC, "createdAt")).skip((long) safePage * safeSize).limit(safeSize);
		query.fields().exclude("history").exclude("steps");
		var items = mongoTemplate.find(query, AgentRun.class).stream()
			.map(r -> summary(r, userEmail.equals(r.getUserEmail()) || team))
			.toList();
		return new AgentData.RunPage(items, safePage, safeSize, total);
	}

	public AgentData.Count pendingApprovalCount(String tenantId, String userEmail) {
		return new AgentData.Count(mongoTemplate.count(
			Query.query(mine(tenantId, userEmail).and("status").is(AgentRun.STATUS_AWAITING_APPROVAL)), AgentRun.class));
	}

	public List<AgentData.ConversationSummary> conversations(String tenantId, String userEmail) {
		var query = Query.query(mine(tenantId, userEmail).and("origin").is(AgentRun.ORIGIN_CHAT))
			.with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(CONVERSATION_SCAN);
		query.fields().include("conversationId", "title", "status", "createdAt");
		var latest = new LinkedHashMap<String, AgentData.ConversationSummary>();
		for (var run : mongoTemplate.find(query, AgentRun.class)) {
			latest.putIfAbsent(run.getConversationId(),
				new AgentData.ConversationSummary(run.getConversationId(), run.getTitle(), run.getStatus(), run.getCreatedAt()));
		}
		return List.copyOf(latest.values());
	}

	public List<AgentData.RunView> conversation(String tenantId, String userEmail, String conversationId) {
		return conversationRuns(tenantId, userEmail, conversationId).stream().map(r -> view(r, true)).toList();
	}

	public void deleteConversation(String tenantId, String userEmail, String conversationId) throws QorvaException {
		var criteria = mine(tenantId, userEmail).and("conversationId").is(conversationId);
		if (mongoTemplate.exists(Query.query(Criteria.where("status").in(AgentRun.ACTIVE_STATUSES)).addCriteria(criteria), AgentRun.class)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.AGENT_CONVERSATION_ACTIVE);
		}
		var removed = mongoTemplate.remove(Query.query(criteria), AgentRun.class).getDeletedCount();
		log.info("agent conversation {} deleted ({} runs) by {}", conversationId, removed, userEmail);
	}

	// ---------------------------------------------------------------------------------------------

	private AgentRun visible(String tenantId, String userEmail, boolean teamView, String runId) throws QorvaException {
		var run = repository.findByIdInTenant(runId, tenantId)
			.filter(r -> teamView || userEmail.equals(r.getUserEmail()))
			.orElseThrow(() -> QorvaErrors.notFound(QorvaErrorCodes.AGENT_RUN_NOT_FOUND));
		return run;
	}

	private List<AgentRun> conversationRuns(String tenantId, String userEmail, String conversationId) {
		return mongoTemplate.find(Query.query(mine(tenantId, userEmail).and("conversationId").is(conversationId))
			.with(Sort.by(Sort.Direction.ASC, "createdAt")), AgentRun.class);
	}

	/** Mentions are re-read from the tenant's data: the name shown and sent to the model is the stored one. */
	private List<AgentRun.Mention> resolveMentions(List<AgentData.MentionView> requested) {
		var resolved = new ArrayList<AgentRun.Mention>();
		if (requested == null) return resolved;
		for (var mention : requested.stream().limit(20).toList()) {
			if (mention == null || mention.id() == null) continue;
			try {
				if ("CV".equals(mention.type())) {
					var cv = cvService.findOneById(mention.id());
					var name = cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null;
					resolved.add(new AgentRun.Mention("CV", cv.getId(), name));
				} else if ("JOB".equals(mention.type())) {
					var job = jobPostService.findOneById(mention.id());
					resolved.add(new AgentRun.Mention("JOB", job.getId(), job.getTitle()));
				}
			} catch (QorvaException e) {
				log.debug("Dropping unknown {} mention {}", mention.type(), mention.id());
			}
		}
		return resolved;
	}

	private static List<AgentRun.HistoryMessage> initialHistory(List<AgentRun> previous, String goal, List<AgentRun.Mention> mentions,
	                                                            AgentRun.Focus focus) {
		var history = new ArrayList<AgentRun.HistoryMessage>();
		var context = previous.stream().filter(r -> AgentRun.STATUS_COMPLETED.equals(r.getStatus()) && r.getFinalAnswer() != null).toList();
		for (var prior : context.subList(Math.max(0, context.size() - CONTEXT_RUNS), context.size())) {
			history.add(AgentHistory.user(userMessageOf(prior)));
			history.add(new AgentRun.HistoryMessage(AgentHistory.ASSISTANT, prior.getFinalAnswer(), null, null));
		}
		var text = new StringBuilder(goal);
		if (!mentions.isEmpty()) {
			text.append("\n\nRecords the recruiter mentioned:");
			for (var m : mentions) {
				text.append("\n- ").append("CV".equals(m.getType()) ? "candidate" : "job").append(' ')
					.append(m.getName() != null ? m.getName() : "(unnamed)")
					.append(" (").append("CV".equals(m.getType()) ? "cvId" : "jobId").append('=').append(m.getId()).append(')');
			}
		}
		if (focus != null) {
			text.append("\n\nConversation focus: candidate ").append(focus.getCvName() != null ? focus.getCvName() : "(unnamed)")
				.append(" (cvId=").append(focus.getCvId()).append(") for the job ")
				.append(focus.getJobTitle() != null ? focus.getJobTitle() : "(untitled)")
				.append(" (jobId=").append(focus.getJobPostId()).append(").");
		}
		history.add(AgentHistory.user(text.toString()));
		return history;
	}

	/** The focus is re-read from the tenant's data, like mentions; one that can't be found is refused. */
	private AgentRun.Focus resolveFocus(AgentData.FocusRequest requested) throws QorvaException {
		if (requested.getCvId() == null || requested.getJobPostId() == null) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.AGENT_FOCUS_INVALID);
		}
		try {
			var cv = cvService.findOneById(requested.getCvId());
			var job = jobPostService.findOneById(requested.getJobPostId());
			var name = cv.getPersonalInformation() != null ? cv.getPersonalInformation().getName() : null;
			return new AgentRun.Focus(cv.getId(), name, job.getId(), job.getTitle());
		} catch (QorvaException e) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.AGENT_FOCUS_INVALID);
		}
	}

	/**
	 * The message the recruiter actually sent in that run, with the records it mentioned (ids included), so a
	 * follow-up like "check all candidates above 60%" still knows which job. Older runs fall back to the goal.
	 */
	static String userMessageOf(AgentRun run) {
		return run.getHistory().reversed().stream()
			.filter(m -> AgentHistory.USER.equals(m.getRole()) && m.getText() != null)
			.map(AgentRun.HistoryMessage::getText)
			.findFirst()
			.orElse(run.getGoal());
	}

	private static String validZone(String zoneId) {
		if (zoneId == null || zoneId.isBlank() || zoneId.length() > 64) return null;
		try {
			return java.time.ZoneId.of(zoneId.strip()).getId();
		} catch (java.time.DateTimeException e) {
			return null;
		}
	}

	static String title(String goal) {
		var oneLine = goal.replaceAll("\\s+", " ").strip();
		return oneLine.length() <= TITLE_LENGTH ? oneLine : oneLine.substring(0, TITLE_LENGTH - 1) + "…";
	}

	private static Criteria tenant(String tenantId) {
		return Criteria.where("tenantId").is(new ObjectId(tenantId));
	}

	private static Criteria mine(String tenantId, String userEmail) {
		return tenant(tenantId).and("userEmail").is(userEmail);
	}

	private static boolean active(AgentRun run) {
		return AgentRun.ACTIVE_STATUSES.contains(run.getStatus());
	}

	static AgentData.RunView view(AgentRun run, boolean mine) {
		return view(run, mine, mine);
	}

	static AgentData.RunView view(AgentRun run, boolean mayCancel, boolean mayApprove) {
		return new AgentData.RunView(
			run.getId(), run.getConversationId(), run.getTitle(), run.getOrigin(), run.getRuleId(), run.getRuleName(),
			run.getUserEmail(), run.getStatus(), run.getGoal(),
			run.getMentions().stream().map(m -> new AgentData.MentionView(m.getType(), m.getId(), m.getName())).toList(),
			run.getFocus() == null ? null : new AgentData.FocusView(run.getFocus().getCvId(), run.getFocus().getCvName(),
				run.getFocus().getJobPostId(), run.getFocus().getJobTitle()),
			run.getSteps().stream().map(s -> new AgentData.StepView(s.getSeq(), s.getKind(), s.getTool(), s.getState(),
				s.getSummaryKey(), s.getSummaryParams(),
				s.getLinks().stream().map(l -> new AgentData.LinkView(l.getType(), l.getId(), l.getLabel())).toList(),
				s.getDraft() == null ? null : new AgentData.DraftView(s.getDraft().getCvId(), s.getDraft().getJobId(),
					s.getDraft().getSubject(), s.getDraft().getBody()), Boolean.TRUE.equals(s.getAutoApproved()))).toList(),
			run.getPendingActions().stream().map(a -> new AgentData.ActionView(a.getActionId(), a.getStepSeq(), a.getTool(),
				a.getStatus(), a.getArgsHash(), a.getPreview(), a.getReason())).toList(),
			run.getApprovalExpiresAt(),
			run.getFinalAnswer(), run.getBlocks(), run.getFailureReason(), Boolean.TRUE.equals(run.getStoppedEarly()),
			mayCancel && active(run) && !run.isCancelRequested(),
			// Only the run's own user decides: the action runs as them (their mailbox, their quota).
			mayApprove && AgentRun.STATUS_AWAITING_APPROVAL.equals(run.getStatus()),
			run.getCreatedAt(), run.getFinishedAt());
	}

	private static AgentData.RunSummary summary(AgentRun run, boolean mayCancel) {
		return new AgentData.RunSummary(run.getId(), run.getConversationId(), run.getTitle(), run.getOrigin(),
			run.getRuleId(), run.getRuleName(), run.getUserEmail(), run.getStatus(), run.getGoal(), run.getStepCount(), run.getFailureReason(),
			mayCancel && active(run) && !run.isCancelRequested(), run.getCreatedAt(), run.getFinishedAt());
	}
}
