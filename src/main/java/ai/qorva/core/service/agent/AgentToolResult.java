package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;

import java.util.List;
import java.util.Map;

/**
 * What a tool returns: {@code data} goes to the model (as JSON, truncated); the summary key, params, links and
 * draft go to the user's timeline. For an approval tool's preview, {@code data} is what the card shows.
 */
public record AgentToolResult(boolean ok, Object data, String summaryKey, Map<String, String> summaryParams,
                              List<AgentRun.Link> links, String error, AgentRun.Draft draft) {

	public static AgentToolResult ok(Object data, String summaryKey, Map<String, String> summaryParams, List<AgentRun.Link> links) {
		return new AgentToolResult(true, data, summaryKey, summaryParams, links, null, null);
	}

	public static AgentToolResult okWithDraft(Object data, String summaryKey, Map<String, String> summaryParams,
	                                          List<AgentRun.Link> links, AgentRun.Draft draft) {
		return new AgentToolResult(true, data, summaryKey, summaryParams, links, null, draft);
	}

	/** A failure the model should see and may work around (bad arguments, not found, not allowed now). */
	public static AgentToolResult error(String message) {
		return new AgentToolResult(false, Map.of("error", message), "agent.step.error", Map.of(), List.of(), message, null);
	}
}
