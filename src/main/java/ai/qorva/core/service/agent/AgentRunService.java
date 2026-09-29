package ai.qorva.core.service.agent;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.repository.AgentRunRepository;
import ai.qorva.core.dto.AgentData;
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
	static final int TITLE_LENGTH = 80;
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
		Integer remaining = usageMonitoringService.findCurrentPeriodByTenantId(tenantId)
			.map(p -> p.getFeatures() != null ? p.getFeatures().getAgentRuns() : null)
			.filter(m -> m != null && m.getLimit() != null)
			.map(m -> Math.max(0, m.getLimit() - (m.getConsumed() != null ? m.getConsumed() : 0)))
			.orElse(null);
		return new AgentData.Availability(properties.isEnabled(), false, remaining, teamView);
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
		if (mongoTemplate.exists(Query.query(mine(tenantId, userEmail).and("status").in(AgentRun.ACTIVE_STATUSES)), AgentRun.class)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.AGENT_RUN_ACTIVE);
		}
		if (!usageMonitoringService.hasCapacityFor(tenantId, UsageMonitoringService.FeatureKey.AGENT_RUNS, 1)) {
			throw QorvaErrors.forbidden(QorvaErrorCodes.USAGE_AGENT_LIMIT_EXCEEDED);
		}

		var mentions = resolveMentions(request.getMentions());
		var previous = request.getConversationId() == null ? List.<AgentRun>of() : conversationRuns(tenantId, userEmail, request.getConversationId());

		var run = new AgentRun();
		run.setTenantId(tenantId);
		run.setUserEmail(userEmail);
		run.setLanguage(language);
		run.setOrigin(AgentRun.ORIGIN_CHAT);
		run.setGoal(goal);
		run.setMentions(mentions);
		run.setStatus(AgentRun.STATUS_QUEUED);
		if (previous.isEmpty()) {
			run.setConversationId(UUID.randomUUID().toString());
			run.setTitle(title(goal));
		} else {
			run.setConversationId(request.getConversationId());
			run.setTitle(previous.getFirst().getTitle());
		}
		run.setHistory(initialHistory(previous, goal, mentions));

		var saved = repository.save(run);
		log.info("agent-run {} queued (tenant={} conversation={})", saved.getId(), tenantId, saved.getConversationId());
		worker.wakeUp();
		return view(saved, true);
	}

	public AgentData.RunView get(String tenantId, String userEmail, boolean teamView, String runId) throws QorvaException {
		var run = visible(tenantId, userEmail, teamView, runId);
		return view(run, userEmail.equals(run.getUserEmail()));
	}

	public AgentData.RunView cancel(String tenantId, String userEmail, boolean teamView, String runId) throws QorvaException {
		var run = visible(tenantId, userEmail, teamView, runId);
		if (store.requestCancel(tenantId, run.getId())) {
			log.info("agent-run {} cancel requested by {}", run.getId(), userEmail);
		}
		return view(visible(tenantId, userEmail, teamView, runId), userEmail.equals(run.getUserEmail()));
	}

	public AgentData.RunPage list(String tenantId, String userEmail, boolean team, String status, String origin,
	                              String user, int page, int size) {
		var criteria = team ? tenant(tenantId) : mine(tenantId, userEmail);
		if (team && user != null && !user.isBlank()) criteria = criteria.and("userEmail").is(user);
		if (status != null && !status.isBlank()) criteria = criteria.and("status").is(status);
		if (origin != null && !origin.isBlank()) criteria = criteria.and("origin").is(origin);
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

	private static List<AgentRun.HistoryMessage> initialHistory(List<AgentRun> previous, String goal, List<AgentRun.Mention> mentions) {
		var history = new ArrayList<AgentRun.HistoryMessage>();
		var context = previous.stream().filter(r -> AgentRun.STATUS_COMPLETED.equals(r.getStatus()) && r.getFinalAnswer() != null).toList();
		for (var prior : context.subList(Math.max(0, context.size() - CONTEXT_RUNS), context.size())) {
			history.add(AgentHistory.user(prior.getGoal()));
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
		history.add(AgentHistory.user(text.toString()));
		return history;
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

	static AgentData.RunView view(AgentRun run, boolean mayCancel) {
		return new AgentData.RunView(
			run.getId(), run.getConversationId(), run.getTitle(), run.getOrigin(), run.getUserEmail(), run.getStatus(),
			run.getGoal(),
			run.getMentions().stream().map(m -> new AgentData.MentionView(m.getType(), m.getId(), m.getName())).toList(),
			run.getSteps().stream().map(s -> new AgentData.StepView(s.getSeq(), s.getKind(), s.getTool(), s.getState(),
				s.getSummaryKey(), s.getSummaryParams(),
				s.getLinks().stream().map(l -> new AgentData.LinkView(l.getType(), l.getId(), l.getLabel())).toList())).toList(),
			run.getFinalAnswer(), run.getFailureReason(), Boolean.TRUE.equals(run.getStoppedEarly()),
			mayCancel && active(run) && !run.isCancelRequested(), false,
			run.getCreatedAt(), run.getFinishedAt());
	}

	private static AgentData.RunSummary summary(AgentRun run, boolean mayCancel) {
		return new AgentData.RunSummary(run.getId(), run.getConversationId(), run.getTitle(), run.getOrigin(),
			run.getUserEmail(), run.getStatus(), run.getGoal(), run.getStepCount(), run.getFailureReason(),
			mayCancel && active(run) && !run.isCancelRequested(), run.getCreatedAt(), run.getFinishedAt());
	}
}
