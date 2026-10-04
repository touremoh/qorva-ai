package ai.qorva.core.service.agent;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.service.ai.AiCallMetrics;
import ai.qorva.core.service.orchestrators.StructuredOutput;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Calls the model with the agent's own system prompt and tool definitions. Uses the ChatModel
 * directly: the shared ChatClient carries a CV-screening default system prompt. Tool execution by
 * Spring AI is disabled — the runner executes, records and (later) pauses each call itself.
 */
@Component
public class AgentModelClient {

	private final ChatModel chatModel;
	private final AgentProperties properties;
	private final AiCallMetrics metrics;

	public AgentModelClient(ChatModel chatModel, AgentProperties properties, AiCallMetrics metrics) {
		this.chatModel = chatModel;
		this.properties = properties;
		this.metrics = metrics;
	}

	public ChatResponse call(List<Message> messages, List<AgentTool> tools) {
		return metrics.record("copilot", properties.getModel(), () -> chatModel.call(new Prompt(messages, options(tools))));
	}

	OpenAiChatOptions options(List<AgentTool> tools) {
		var builder = OpenAiChatOptions.builder()
			.model(properties.getModel())
			.temperature(StructuredOutput.temperatureFor(properties.getModel(), 0.2))
			.toolCallbacks(tools.stream().map(AgentModelClient::declared).toList())
			.internalToolExecutionEnabled(false);
		// Not sending it lets the model apply its own reasoning default, and OpenAI then refuses the tools (HTTP 400).
		if (StringUtils.hasText(properties.getReasoningEffort())) {
			builder.reasoningEffort(properties.getReasoningEffort().strip());
		}
		return builder.build();
	}

	/** Declares a tool to the model without giving Spring AI a way to run it. */
	static ToolCallback declared(AgentTool tool) {
		var definition = ToolDefinition.builder()
			.name(tool.name())
			.description(tool.description())
			.inputSchema(tool.inputSchema())
			.build();
		return new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String toolInput) {
				throw new IllegalStateException("Agent tools are executed by AgentRunner, not by Spring AI");
			}
		};
	}
}
