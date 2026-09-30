package ai.qorva.core.spike;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * THROWAWAY spike for the Copilot guide (§ 6.0) — branch spike/agent-tool-calling, never merged.
 * Offline tests always run; the live test runs only with SPRING_AI_OPENAI_API_KEY exported.
 */
class AgentToolCallingSpikeTest {

	// ---- persistence: what the agent_runs.history field would hold ---------------------------

	/** Candidate storage shape for one conversation message. */
	record StoredMessage(String role, String text, List<StoredToolCall> toolCalls, List<StoredToolResponse> toolResponses) {
	}

	record StoredToolCall(String id, String name, String arguments) {
	}

	record StoredToolResponse(String id, String name, String data) {
	}

	static StoredMessage store(Message m) {
		return switch (m) {
			case SystemMessage s -> new StoredMessage("system", s.getText(), null, null);
			case UserMessage u -> new StoredMessage("user", u.getText(), null, null);
			case AssistantMessage a -> new StoredMessage("assistant", a.getText(),
				a.getToolCalls().stream().map(c -> new StoredToolCall(c.id(), c.name(), c.arguments())).toList(), null);
			case ToolResponseMessage t -> new StoredMessage("tool", null, null,
				t.getResponses().stream().map(r -> new StoredToolResponse(r.id(), r.name(), r.responseData())).toList());
			default -> throw new IllegalArgumentException("Unsupported message " + m.getClass());
		};
	}

	static Message load(StoredMessage s) {
		return switch (s.role()) {
			case "system" -> new SystemMessage(s.text());
			case "user" -> new UserMessage(s.text());
			case "assistant" -> new AssistantMessage(s.text() == null ? "" : s.text(), Map.of(),
				s.toolCalls() == null ? List.of() : s.toolCalls().stream()
					.map(c -> new AssistantMessage.ToolCall(c.id(), "function", c.name(), c.arguments())).toList());
			case "tool" -> new ToolResponseMessage(s.toolResponses().stream()
				.map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(), r.data())).toList());
			default -> throw new IllegalArgumentException("Unknown role " + s.role());
		};
	}

	private static List<Message> sampleHistory() {
		return List.of(
			new SystemMessage("You are Qorva Copilot."),
			new UserMessage("How many Java developers in Lisbon?"),
			new AssistantMessage("", Map.of(), List.of(
				new AssistantMessage.ToolCall("call_1", "function", "count_candidates", "{\"city\":\"Lisbon\",\"skill\":\"Java\"}"))),
			new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse("call_1", "count_candidates", "{\"count\":7}"))),
			new AssistantMessage("There are 7 Java developers in Lisbon."));
	}

	@Test
	void springAiMessagesDoNotRoundTripThroughPlainJackson() throws Exception {
		var mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(sampleHistory());

		// Message is an interface without type info: storing Spring AI objects directly is not an option.
		assertThatThrownBy(() -> mapper.readValue(json, new TypeReference<List<Message>>() {}))
			.isInstanceOf(Exception.class);
	}

	@Test
	void ownStorageShapeRoundTripsEveryMessageKind() throws Exception {
		var mapper = new ObjectMapper();
		var original = sampleHistory();

		String json = mapper.writeValueAsString(original.stream().map(AgentToolCallingSpikeTest::store).toList());
		List<Message> restored = mapper.readValue(json, new TypeReference<List<StoredMessage>>() {})
			.stream().map(AgentToolCallingSpikeTest::load).toList();

		assertThat(restored).hasSize(original.size());
		for (int i = 0; i < original.size(); i++) {
			assertThat(restored.get(i).getMessageType()).isEqualTo(original.get(i).getMessageType());
			assertThat(store(restored.get(i))).isEqualTo(store(original.get(i)));
		}
	}

	// ---- live: does the model accept tools at temperature 1.0, with our own loop? -------------

	/** Declares a tool to the model without letting Spring AI execute it (internal execution is off). */
	private static ToolCallback declared(String name, String description, String schema) {
		var definition = ToolDefinition.builder().name(name).description(description).inputSchema(schema).build();
		return new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String toolInput) {
				throw new IllegalStateException("Spring AI must not execute tools: the agent loop does");
			}
		};
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "SPRING_AI_OPENAI_API_KEY", matches = ".+")
	void liveModelCallsToolsAtTemperatureOneAndUsesTheResults() {
		String model = System.getenv().getOrDefault("QORVA_AGENT_MODEL", "gpt-5.6-terra");
		var chatModel = OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().apiKey(System.getenv("SPRING_AI_OPENAI_API_KEY")).build())
			.build();
		var tool = declared("count_candidates", "Counts candidates in the library by city and skill.", """
			{"type":"object","properties":{"city":{"type":"string"},"skill":{"type":"string"}},
			 "required":["city","skill"],"additionalProperties":false}""");
		var options = OpenAiChatOptions.builder()
			.model(model)
			.temperature(1.0)
			.reasoningEffort("none") // as AgentModelClient: required for tools on chat completions
			.toolCallbacks(List.of(tool))
			.internalToolExecutionEnabled(false)
			.build();

		List<Message> history = new ArrayList<>(List.of(
			new SystemMessage("You are a recruiting assistant. Use tools for any number; never guess."),
			new UserMessage("How many Java developers do we have in Lisbon, and how many in Porto?")));

		// Turn 1: the model must answer with tool calls, not text.
		var first = chatModel.call(new Prompt(history, options));
		var assistant = first.getResult().getOutput();
		System.out.printf("SPIKE model=%s toolCalls=%d usage=%s%n", model, assistant.getToolCalls().size(),
			first.getMetadata().getUsage());
		assistant.getToolCalls().forEach(c -> System.out.printf("SPIKE call id=%s name=%s args=%s%n", c.id(), c.name(), c.arguments()));
		assertThat(assistant.hasToolCalls()).as("model returned tool calls").isTrue();
		assertThat(assistant.getToolCalls()).allSatisfy(c -> assertThat(c.name()).isEqualTo("count_candidates"));

		// Our loop executes them, then round-trips the history through storage before turn 2.
		history.add(assistant);
		history.add(new ToolResponseMessage(assistant.getToolCalls().stream()
			.map(c -> new ToolResponseMessage.ToolResponse(c.id(), c.name(),
				c.arguments().contains("Porto") ? "{\"count\":3}" : "{\"count\":7}"))
			.toList()));
		List<Message> reloaded = history.stream().map(AgentToolCallingSpikeTest::store).map(AgentToolCallingSpikeTest::load).toList();

		// Turn 2: the model uses the results and answers in text.
		var second = chatModel.call(new Prompt(reloaded, options));
		String answer = second.getResult().getOutput().getText();
		System.out.printf("SPIKE answer=%s%nSPIKE usage=%s%n", answer, second.getMetadata().getUsage());
		assertThat(second.getResult().getOutput().hasToolCalls()).as("no further tool calls needed").isFalse();
		assertThat(answer).contains("7");
	}
}
