package ai.qorva.core.service.agent;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.enums.UserActionsEnum;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentModelClientTest {

	private static final AgentTool SEARCH = new AgentTool() {
		public String name() { return "search_cvs"; }
		public String description() { return "Search"; }
		public String inputSchema() { return "{\"type\":\"object\"}"; }
		public AgentRiskTier tier() { return AgentRiskTier.READ; }
		public Set<UserActionsEnum> requiredActions() { return Set.of(); }
		public AgentToolResult execute(JsonNode args, AgentToolContext ctx) { return null; }
	};

	@Test
	void sendsReasoningEffortNoneWithTheToolsAndKeepsExecutionToUs() {
		var chatModel = mock(ChatModel.class);
		when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
		var properties = new AgentProperties();

		new AgentModelClient(chatModel, properties).call(List.of(new UserMessage("hi")), List.of(SEARCH));

		var prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(prompt.capture());
		var options = (OpenAiChatOptions) prompt.getValue().getOptions();
		// gpt-5.x refuses function tools on chat completions unless reasoning_effort is explicitly "none".
		assertThat(options.getReasoningEffort()).isEqualTo("none");
		assertThat(options.getModel()).isEqualTo(properties.getModel());
		assertThat(options.getInternalToolExecutionEnabled()).isFalse();
		assertThat(options.getToolCallbacks()).extracting(c -> c.getToolDefinition().name()).containsExactly("search_cvs");
	}

	@Test
	void aBlankReasoningEffortIsNotSent() {
		var properties = new AgentProperties();
		properties.setReasoningEffort(" ");

		var options = new AgentModelClient(mock(ChatModel.class), properties).options(List.of());

		assertThat(options.getReasoningEffort()).isNull();
	}
}
