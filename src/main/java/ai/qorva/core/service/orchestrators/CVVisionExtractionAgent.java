package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.CVOutputDTO;
import ai.qorva.core.dto.QorvaPromptContextHolder;
import ai.qorva.core.utils.CVPageImageRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Vision counterpart of {@link CVExtractionAgent}: extracts the structured profile from
 * rendered page images (plus whatever text the parser found), so information hidden in
 * pictures — designed headers, scanned pages, photo captions — is no longer lost.
 * Only the escalated minority of CVs comes through here (see VisionEscalationPolicy).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CVVisionExtractionAgent {

	private final ChatClient chatClient;
	private final QorvaPromptContextHolder promptContextHolder;

	/** Vision pass needs a multimodal model; mini tier — quality matters more here than on the text pass. */
	@Value("${qorva.ai.extraction.vision-model:gpt-4.1-mini}")
	private String visionModel;

	public String extract(String partialText, List<CVPageImageRenderer.PageImage> pages) {
		var converter = new BeanOutputConverter<>(CVOutputDTO.class);
		var promptTemplate = promptContextHolder.getCvContentExtractionPromptTemplate();
		var cvOutputFormat = promptContextHolder.getCvOutputFormat();

		var media = pages.stream()
			.map(page -> Media.builder()
				.mimeType(MimeType.valueOf(page.mimeType()))
				.data(new ByteArrayResource(page.bytes()))
				.build())
			.toArray(Media[]::new);

		var textHint = StringUtils.hasText(partialText)
			? partialText
			: "(no machine-readable text layer — read the CV entirely from the attached page images)";

		try {
			return chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(visionModel)
					.responseFormat(ResponseFormat.builder()
						.type(ResponseFormat.Type.JSON_SCHEMA)
						.jsonSchema(ResponseFormat.JsonSchema.builder()
							.name("cv_parser")
							.schema(converter.getJsonSchema())
							.strict(Boolean.FALSE)
							.build())
						.build())
					.temperature(0.1)
					.build())
				.user(u -> u
					.text(promptTemplate
						+ "\n\nThe attached images are the CV's rendered pages; they are authoritative."
						+ " The CV Content text below may be incomplete — information visible in the"
						+ " images but absent from the text (names, contact details, headers) MUST be extracted.")
					.param("cv_data", textHint)
					.param("output_format", cvOutputFormat)
					.media(media))
				.call()
				.content();
		} catch (Exception e) {
			log.error("Error while extracting CV from page images: {}", e.getMessage());
			return null;
		}
	}
}
