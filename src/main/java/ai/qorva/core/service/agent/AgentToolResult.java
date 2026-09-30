package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;

import java.util.List;
import java.util.Map;

/**
 * What a tool returns: {@code data} goes to the model (as JSON, truncated); the summary key,
 * params and links go to the user's timeline.
 */
public record AgentToolResult(boolean ok, Object data, String summaryKey, Map<String, String> summaryParams,
                              List<AgentRun.Link> links, String error) {

	public static AgentToolResult ok(Object data, String summaryKey, Map<String, String> summaryParams, List<AgentRun.Link> links) {
		return new AgentToolResult(true, data, summaryKey, summaryParams, links, null);
	}

	/** A failure the model should see and may work around (bad arguments, not found). */
	public static AgentToolResult error(String message) {
		return new AgentToolResult(false, Map.of("error", message), "agent.step.error", Map.of(), List.of(), message);
	}
}
