package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.CVQueryParams;
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

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_1_MINI;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightEntityExtractor {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;
	private final ObjectMapper objectMapper;

	public CVQueryParams extract(String question, InsightIntent intent) {
		if (intent == InsightIntent.GENERAL_RECRUITING_QUESTION) {
			return CVQueryParams.empty();
		}

		var converter = new BeanOutputConverter<>(CVQueryParams.class);
		var promptTemplate = promptContextHolder.getEntityExtractorPrompt(intent);

		try {
			String renderedPrompt = promptTemplate.replace("{{question}}", question);

			String content = chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(GPT_4_1_MINI)
					.responseFormat(ResponseFormat.builder()
						.type(ResponseFormat.Type.JSON_SCHEMA)
						.jsonSchema(ResponseFormat.JsonSchema.builder()
							.name("entity_extractor")
							.schema(converter.getJsonSchema())
							.strict(Boolean.FALSE)
							.build())
						.build())
					.temperature(0.0)
					.build())
				.messages(new UserMessage(renderedPrompt))
				.call()
				.content();

			return objectMapper.readValue(content, CVQueryParams.class);
		} catch (Exception e) {
			log.error("Error extracting entities from question, using empty params: {}", e.getMessage());
			return CVQueryParams.empty();
		}
	}
}
