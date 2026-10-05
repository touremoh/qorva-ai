package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.AnswerBlocks;
import ai.qorva.core.dto.ConversationFrame;

import java.util.List;
import java.util.Map;

/**
 * What a tool returns: {@code data} goes to the model (as JSON, truncated); the summary key, params, links and
 * draft go to the user's timeline. For an approval tool's preview, {@code data} is what the card shows.
 * A terminal tool's {@code answer} is shown to the user as the run's answer, as is.
 */
public record AgentToolResult(boolean ok, Object data, String summaryKey, Map<String, String> summaryParams,
                              List<AgentRun.Link> links, String error, AgentRun.Draft draft, AgentAnswer answer) {

	/** The run's answer from a terminal tool: the engine's text, plus a library analysis's blocks and frame. */
	public record AgentAnswer(String text, AnswerBlocks blocks, ConversationFrame insightFrame) {}

	public AgentToolResult(boolean ok, Object data, String summaryKey, Map<String, String> summaryParams,
	                       List<AgentRun.Link> links, String error, AgentRun.Draft draft) {
		this(ok, data, summaryKey, summaryParams, links, error, draft, null);
	}

	public static AgentToolResult ok(Object data, String summaryKey, Map<String, String> summaryParams, List<AgentRun.Link> links) {
		return new AgentToolResult(true, data, summaryKey, summaryParams, links, null, null);
	}

	public static AgentToolResult okWithDraft(Object data, String summaryKey, Map<String, String> summaryParams,
	                                          List<AgentRun.Link> links, AgentRun.Draft draft) {
		return new AgentToolResult(true, data, summaryKey, summaryParams, links, null, draft);
	}

	/** A terminal tool's answer, which ends the run; {@code data} is what the model would see if it continued. */
	public static AgentToolResult answer(AgentAnswer answer, Object data, String summaryKey, Map<String, String> summaryParams,
	                                     List<AgentRun.Link> links) {
		return new AgentToolResult(true, data, summaryKey, summaryParams, links, null, null, answer);
	}

	/** A failure the model should see and may work around (bad arguments, not found, not allowed now). */
	public static AgentToolResult error(String message) {
		return new AgentToolResult(false, Map.of("error", message), "agent.step.error", Map.of(), List.of(), message, null);
	}
}
