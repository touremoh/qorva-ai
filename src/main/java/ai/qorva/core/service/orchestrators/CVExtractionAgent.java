package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CVExtractionAgent {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;

	/**
	 * Text-pass model. Config so the nano-tier switch (cheaper, faster) is one env var
	 * after benchmarking — the vision escalation pass catches what a smaller model fumbles.
	 */
	@Value("${qorva.ai.extraction.model:gpt-4.1-mini}")
	private String extractionModel;

	public String extract(String cvContent) {
		var converter = new BeanOutputConverter<>(CVOutputDTO.class);
		var promptTemplate = promptContextHolder.getCvContentExtractionPromptTemplate();
		var cvOutputFormat = promptContextHolder.getCvOutputFormat();

		try {
			return chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(extractionModel)
					.responseFormat(ResponseFormat.builder()
						.type(ResponseFormat.Type.JSON_SCHEMA)
						.jsonSchema(ResponseFormat.JsonSchema.builder()
							.name("cv_parser")
							.schema(converter.getJsonSchema())
							.strict(Boolean.FALSE)
							.build())
						.build())
					// Extraction wants determinism, not creativity — but GPT-5-family models
					// only accept the default temperature (1), so pick per model family.
					.temperature(extractionModel.startsWith("gpt-5") ? 1.0 : 0.1)
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
