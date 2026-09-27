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
import org.springframework.stereotype.Service;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_1;

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
				// Extraction is the one decision that changes results (which filters run), and the prompt
				// is the largest in the pipeline. It gets the full model; the other insight calls stay on mini.
				.options(StructuredOutput.options(GPT_4_1, "entity_extractor", converter.getJsonSchema(), false, 0.0))
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
