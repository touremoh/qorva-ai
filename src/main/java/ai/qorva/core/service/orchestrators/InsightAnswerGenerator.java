package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.AnswerGenerationResult;
import ai.qorva.core.dto.InsightHandlerResult;
import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_1_MINI;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightAnswerGenerator {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;
	private final ObjectMapper objectMapper;

	public AnswerGenerationResult generate(InsightHandlerResult result, InsightIntent intent, String originalQuestion) {
		var converter = new BeanOutputConverter<>(AnswerGenerationResult.class);
		var promptTemplate = promptContextHolder.getInsightAnswerGeneratorPrompt();

		try {
			String resultJson = objectMapper.writeValueAsString(result);

			String renderedPrompt = promptTemplate
				.replace("{{intent}}", intent.name())
				.replace("{{question}}", originalQuestion)
				.replace("{{handler_result_json}}", resultJson);

			String content = chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(GPT_4_1_MINI)
					.responseFormat(ResponseFormat.builder()
						.type(ResponseFormat.Type.JSON_SCHEMA)
						.jsonSchema(ResponseFormat.JsonSchema.builder()
							.name("insight_answer")
							.schema(converter.getJsonSchema())
							.strict(Boolean.FALSE)
							.build())
						.build())
					.temperature(0.3)
					.build())
				.messages(new UserMessage(renderedPrompt))
				.call()
				.content();

			return objectMapper.readValue(content, AnswerGenerationResult.class);
		} catch (Exception e) {
			log.error("Error generating insight answer: {}", e.getMessage());
			return new AnswerGenerationResult(
				null,
				"I was unable to generate a summary at this time. Please try again.",
				List.of(),
				null
			);
		}
	}
}
