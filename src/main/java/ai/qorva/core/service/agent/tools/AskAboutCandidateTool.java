package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.NoteService;
import ai.qorva.core.enums.NoteTargetTypeEnum;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentRunStore;
import ai.qorva.core.service.agent.AgentRunner;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import ai.qorva.core.service.orchestrators.CandidateAnswerEngine;
import ai.qorva.core.service.orchestrators.ConversationTurn;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recruiter's question about one candidate for one job, answered by the candidate answer engine (the whole CV,
 * the job with its scoring rules and the screening report). Terminal: its answer is the run's answer. The question
 * is the recruiter's own message, never the model's rewording; the model only picks the candidate and the job.
 */
@Component
public class AskAboutCandidateTool implements AgentTool {

	static final String LIMIT_REACHED = "The plan's monthly limit of candidate questions is reached. Tell the recruiter.";
	static final String TOO_LONG = "The answer took too long to write and was cut off; nothing is wrong with the service. Tell the "
		+ "recruiter, and suggest asking for a shorter answer (for example one part of it at a time) or trying again.";
	static final String JOB_REQUIRED = "jobId is required: use the conversation focus or a mentioned job, or find the "
		+ "candidate's jobs with list_reports; if there are several, ask the recruiter which one.";

	private final CandidateAnswerEngine engine;
	private final AgentRunStore store;
	private final CVService cvService;
	private final JobPostService jobPostService;
	private final UsageMonitoringService usageMonitoringService;
	private final NoteService noteService;

	public AskAboutCandidateTool(CandidateAnswerEngine engine, AgentRunStore store, CVService cvService,
	                             JobPostService jobPostService, UsageMonitoringService usageMonitoringService, NoteService noteService) {
		this.engine = engine;
		this.store = store;
		this.cvService = cvService;
		this.jobPostService = jobPostService;
		this.usageMonitoringService = usageMonitoringService;
		this.noteService = noteService;
	}

	@Override
	public String name() {
		return "ask_about_candidate";
	}

	@Override
	public String description() {
		return "Answer the recruiter's question about one candidate, usually for one job: fit, strengths, gaps, red flags, "
			+ "the screening score, interview questions. Reads the whole CV, the job with its scoring rules and the screening "
			+ "report. Its answer goes to the recruiter as is and ends your work. cvId and jobId default to the conversation "
			+ "focus, then to the mentioned candidate and job. saveAsNote keeps the answer as a note on the candidate's match "
			+ "report (e.g. an interview plan); in a task started by a rule, always set it — nobody reads the answer there — and "
			+ "call this once per candidate: a saved answer does not end your work, you get a short confirmation instead. "
			+ "question (rule tasks only) is what to ask about this candidate, taken from the rule's goal.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{"cvId":{"type":"string"},"jobId":{"type":"string"},
			  "saveAsNote":{"type":"boolean","description":"Keep the answer as a note on the candidate's match report"},
			  "question":{"type":"string","maxLength":1000,"description":"Rule tasks only: the question about this candidate"}},
			 "additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.READ;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.VIEW_CV, UserActionsEnum.VIEW_JOB);
	}

	/** Chat, and rule tasks, which save the answer as a note (nobody reads a rule task's answer). */
	@Override
	public boolean available(AgentToolContext ctx) {
		return AgentRun.ORIGIN_CHAT.equals(ctx.origin()) || AgentRun.ORIGIN_RULE.equals(ctx.origin());
	}

	@Override
	public boolean terminal() {
		return true;
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var run = store.find(ctx.tenantId(), ctx.runId()).orElse(null);
		if (run == null) return AgentToolResult.error("This tool only works inside a Copilot conversation.");
		var cvId = pick(ToolArgs.text(args, "cvId"), run.getFocus() != null ? run.getFocus().getCvId() : null, run, "CV");
		if (cvId == null) return AgentToolResult.error("cvId is required: find the candidate first, or ask the recruiter which one.");
		var jobId = pick(ToolArgs.text(args, "jobId"), run.getFocus() != null ? run.getFocus().getJobPostId() : null, run, "JOB");
		if (jobId == null) return AgentToolResult.error(JOB_REQUIRED);
		if (!usageMonitoringService.hasCapacityFor(ctx.tenantId(), UsageMonitoringService.FeatureKey.AI_RESUME_CHATS, 1)) {
			return AgentToolResult.error(LIMIT_REACHED);
		}

		boolean fromRule = AgentRun.ORIGIN_RULE.equals(ctx.origin());
		boolean save = args.path("saveAsNote").asBoolean(false) || fromRule;
		if (save && !noteService.canWrite(NoteTargetTypeEnum.MATCHING_REPORT)) {
			return AgentToolResult.error("The recruiter can't add notes to match reports, so the answer can't be saved.");
		}
		// The recruiter's own words in chat; in a rule task, the rule's question about this candidate.
		var question = fromRule && ToolArgs.text(args, "question") != null
			? ToolArgs.truncate(ToolArgs.text(args, "question"), 1000) : run.getGoal();

		// Tenant-scoped lookups: an id from another tenant is simply not found.
		var cv = cvService.findOneById(cvId);
		var job = jobPostService.findOneById(jobId);
		CandidateAnswerEngine.Answer answer;
		try {
			answer = engine.answer(ctx.tenantId(), cv.getId(), job.getId(), AgentRunner.languageName(ctx.language()),
				fromRule ? List.of() : earlierTurns(store.earlierInConversation(run)), question);
		} catch (QorvaException e) {
			if (QorvaErrorCodes.AI_ANSWER_TOO_LONG.equals(e.getMessage())) return AgentToolResult.error(TOO_LONG);
			throw e;
		}

		var name = CvProjections.name(cv);
		var links = new ArrayList<AgentRun.Link>();
		links.add(new AgentRun.Link("CV", cv.getId(), name));
		links.add(new AgentRun.Link("JOB", job.getId(), job.getTitle()));
		if (answer.matchingReportId() != null) {
			links.add(new AgentRun.Link("REPORT", answer.matchingReportId(), job.getTitle()));
		}
		var params = Map.of("name", name != null ? name : "", "job", job.getTitle() != null ? job.getTitle() : "");
		if (save) {
			// The report when there is one (the answer is about this candidate for this job), else the candidate.
			var type = answer.matchingReportId() != null ? NoteTargetTypeEnum.MATCHING_REPORT : NoteTargetTypeEnum.CV;
			var targetId = answer.matchingReportId() != null ? answer.matchingReportId() : cv.getId();
			noteService.createFromCopilot(ctx.tenantId(), ctx.userEmail(), type, targetId, answer.text(), ctx.runId());
			if (fromRule) {
				// Not the run's answer: the task goes on with the next candidate, and the model gets a confirmation only.
				return AgentToolResult.ok(Map.of("savedAsNote", true, "on", type.name(), "excerpt", ToolArgs.truncate(answer.text(), 300)),
					"agent.step.ask_about_candidate_saved", params, links);
			}
		}
		return AgentToolResult.answer(new AgentToolResult.AgentAnswer(answer.text(), null, null),
			Map.of("answer", answer.text(), "savedAsNote", save), save ? "agent.step.ask_about_candidate_saved" : "agent.step.ask_about_candidate",
			params, links);
	}

	/** The argument, else the focus, else the only mentioned record of that type. */
	private static String pick(String argument, String focus, AgentRun run, String mentionType) {
		if (argument != null) return argument;
		if (focus != null) return focus;
		var mentioned = run.getMentions().stream().filter(m -> mentionType.equals(m.getType())).toList();
		return mentioned.size() == 1 ? mentioned.getFirst().getId() : null;
	}

	/** Every earlier exchange of the conversation; the engine keeps the newest ones that fit its budget. */
	static List<ConversationTurn> earlierTurns(List<AgentRun> earlier) {
		var turns = new ArrayList<ConversationTurn>();
		for (var prior : earlier) {
			if (prior.getFinalAnswer() == null) continue;
			turns.add(ConversationTurn.recruiter(prior.getGoal()));
			turns.add(ConversationTurn.assistant(prior.getFinalAnswer()));
		}
		return turns;
	}
}
