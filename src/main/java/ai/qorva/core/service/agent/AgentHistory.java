package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

/**
 * Converts between Spring AI messages and the stored {@link AgentRun.HistoryMessage}: Spring AI's
 * Message is an interface without type information, so it can't be persisted as is (spike 2026-09-29).
 */
final class AgentHistory {

	static final String SYSTEM = "system";
	static final String USER = "user";
	static final String ASSISTANT = "assistant";
	static final String TOOL = "tool";

	private AgentHistory() {
	}

	static AgentRun.HistoryMessage user(String text) {
		return new AgentRun.HistoryMessage(USER, text, null, null);
	}

	static AgentRun.HistoryMessage assistant(AssistantMessage message) {
		var calls = message.getToolCalls().stream()
			.map(c -> new AgentRun.ToolCall(c.id(), c.name(), c.arguments()))
			.toList();
		return new AgentRun.HistoryMessage(ASSISTANT, message.getText(), calls.isEmpty() ? null : calls, null);
	}

	static AgentRun.HistoryMessage toolResults(List<AgentRun.ToolResult> results) {
		return new AgentRun.HistoryMessage(TOOL, null, null, results);
	}

	/** The system prompt is not stored: it is rebuilt on every step so prompt fixes apply to resumed runs. */
	static List<Message> toMessages(String systemPrompt, List<AgentRun.HistoryMessage> history) {
		var messages = new java.util.ArrayList<Message>();
		messages.add(new SystemMessage(systemPrompt));
		for (var stored : history) {
			messages.add(switch (stored.getRole()) {
				case USER -> new UserMessage(stored.getText());
				case ASSISTANT -> new AssistantMessage(stored.getText() == null ? "" : stored.getText(), Map.of(),
					stored.getToolCalls() == null ? List.of() : stored.getToolCalls().stream()
						.map(c -> new AssistantMessage.ToolCall(c.getId(), "function", c.getName(), c.getArguments()))
						.toList());
				case TOOL -> new ToolResponseMessage(stored.getToolResults().stream()
					.map(r -> new ToolResponseMessage.ToolResponse(r.getId(), r.getName(), r.getData()))
					.toList());
				default -> throw new IllegalStateException("Unknown stored role " + stored.getRole());
			});
		}
		return messages;
	}
}
