package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.IntentClassificationResult;
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

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_1_MINI;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightIntentClassifier {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;
	private final ObjectMapper objectMapper;

	public InsightIntent classify(String question) {
		var converter = new BeanOutputConverter<>(IntentClassificationResult.class);
		var promptTemplate = promptContextHolder.getIntentClassifierPrompt();

		try {
			String renderedPrompt = promptTemplate.replace("{{question}}", question);

			String content = chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(GPT_4_1_MINI)
					.responseFormat(ResponseFormat.builder()
						.type(ResponseFormat.Type.JSON_SCHEMA)
						.jsonSchema(ResponseFormat.JsonSchema.builder()
							.name("intent_classifier")
							.schema(converter.getJsonSchema())
							.strict(Boolean.FALSE)
							.build())
						.build())
					.temperature(0.0)
					.build())
				.messages(new UserMessage(renderedPrompt))
				.call()
				.content();

			var result = objectMapper.readValue(content, IntentClassificationResult.class);
			return InsightIntent.valueOf(result.intent());
		} catch (IllegalArgumentException e) {
			log.warn("LLM returned unknown intent value for question, falling back to GENERAL: {}", e.getMessage());
			return InsightIntent.GENERAL_RECRUITING_QUESTION;
		} catch (Exception e) {
			log.error("Error classifying intent: {}", e.getMessage());
			return InsightIntent.GENERAL_RECRUITING_QUESTION;
		}
	}
}
