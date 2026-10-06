package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.AnswerBlocks;
import ai.qorva.core.dto.ConversationFrame;
import ai.qorva.core.dto.MentionDTO;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.service.LibraryInsightsService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentRunStore;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recruiter's question about the library as a whole, answered by Talent Intelligence (intent, filters, one
 * deterministic handler, then the wording). Terminal: its answer, charts and candidate cards are the run's answer.
 * The question is the recruiter's own message; a follow-up keeps the previous analysis's filters through the frame
 * stored on the conversation's previous run.
 */
@Component
public class AnalyzeLibraryTool implements AgentTool {

	static final String LIMIT_REACHED = "The plan's monthly limit of library analyses is reached. Tell the recruiter.";

	private final LibraryInsightsService insightsService;
	private final AgentRunStore store;
	private final UsageMonitoringService usageMonitoringService;

	public AnalyzeLibraryTool(LibraryInsightsService insightsService, AgentRunStore store,
	                          UsageMonitoringService usageMonitoringService) {
		this.insightsService = insightsService;
		this.store = store;
		this.usageMonitoringService = usageMonitoringService;
	}

	@Override
	public String name() {
		return "analyze_library";
	}

	@Override
	public String description() {
		return "Answer the recruiter's question about the resume library as a whole: how many or which profiles match, "
			+ "distributions of skills, seniority, locations or salaries, clusters, skill gaps, rediscovering past candidates, "
			+ "comparing candidates, resume data quality. Returns charts and candidate cards. Its answer goes to the recruiter "
			+ "as is and ends your work. Takes no arguments: it answers the recruiter's message.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{},"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.READ;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.VIEW_CV);
	}

	/** Chat only: a rule's run reports what it did, nobody reads an analysis there. */
	@Override
	public boolean available(AgentToolContext ctx) {
		return AgentRun.ORIGIN_CHAT.equals(ctx.origin());
	}

	@Override
	public boolean terminal() {
		return true;
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
		var run = store.find(ctx.tenantId(), ctx.runId()).orElse(null);
		if (run == null) return AgentToolResult.error("This tool only works inside a Copilot conversation.");
		if (!usageMonitoringService.hasCapacityFor(ctx.tenantId(), UsageMonitoringService.FeatureKey.TALENT_INTELLIGENCE_QUERIES, 1)) {
			return AgentToolResult.error(LIMIT_REACHED);
		}

		var analysis = insightsService.analyse(run.getGoal(), mentions(run), ctx.tenantId(),
			previousFrame(store.earlierInConversation(run)));
		var response = analysis.response();

		var data = new LinkedHashMap<String, Object>();
		data.put("answer", response.answerText());
		data.put("totalCandidateCount", response.totalCandidateCount());
		return AgentToolResult.answer(
			new AgentToolResult.AgentAnswer(response.answerText(), AnswerBlocks.of(response), analysis.frame()),
			data, "agent.step.analyze_library",
			Map.of("intent", response.intent() != null ? response.intent().name() : ""), List.of());
	}

	/** Only the immediately previous exchange carries over: an older analysis would leak a stale topic. */
	static ConversationFrame previousFrame(List<AgentRun> earlier) {
		return earlier.isEmpty() ? null : earlier.getLast().getInsightFrame();
	}

	private static List<MentionDTO> mentions(AgentRun run) {
		return run.getMentions().stream()
			.map(m -> new MentionDTO("CV".equals(m.getType()) ? MentionDTO.TYPE_CANDIDATE : MentionDTO.TYPE_JOB, m.getId(), m.getName()))
			.toList();
	}
}
