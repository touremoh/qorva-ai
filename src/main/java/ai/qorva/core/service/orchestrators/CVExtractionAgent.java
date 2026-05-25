package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_1_MINI;

@Slf4j
@Service
@RequiredArgsConstructor
public class CVExtractionAgent {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;

	public String extract(String cvContent) {
		var converter = new BeanOutputConverter<>(CVOutputDTO.class);
		var promptTemplate = promptContextHolder.getCvContentExtractionPromptTemplate();
		var cvOutputFormat = promptContextHolder.getCvOutputFormat();

		try {
			return chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(GPT_4_1_MINI)
					.responseFormat(ResponseFormat.builder()
						.type(ResponseFormat.Type.JSON_SCHEMA)
						.jsonSchema(ResponseFormat.JsonSchema.builder()
							.name("cv_parser")
							.schema(converter.getJsonSchema())
							.strict(Boolean.FALSE)
							.build())
						.build())
					.temperature(1.0)
					.build())
				.user(u -> u
					.text(promptTemplate)
					.param("cv_data", cvContent)
					.param("output_format", cvOutputFormat))
				.call()
				.content();
		} catch (Exception e) {
			log.error("Error while extracting CV from the content: {}", e.getMessage());
			return null;
		}
	}
}
