package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.LibraryQualityInsight;
import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Writes the Library Quality summary: {@link #generate} reads the report in English,
 * {@link #translate} carries that text into another language without changing its content.
 */
@Slf4j
@Service
public class LibraryQualityInsightAgent {

	private final ChatClient chatClient;
	private final ObjectMapper objectMapper;
	private final String insightPrompt;
	private final String translatePrompt;

	/** Short prose over a small aggregate — the mini tier is plenty; unmetered like the outreach drafts. */
	@Value("${qorva.ai.library-quality-insight.model:gpt-4.1-mini}")
	private String model;

	public LibraryQualityInsightAgent(ChatClient chatClient, ObjectMapper objectMapper) throws QorvaException {
		this.chatClient = chatClient;
		this.objectMapper = objectMapper;
		this.insightPrompt = readPrompt("Library_quality_insight_prompt.md");
		this.translatePrompt = readPrompt("Library_quality_insight_translate_prompt.md");
	}

	public LibraryQualityInsight.Draft generate(LibraryQualityReport report) throws QorvaException {
		try {
			var converter = new BeanOutputConverter<>(LibraryQualityInsight.Draft.class);
			var prompt = insightPrompt.replace("{{report_json}}", objectMapper.writeValueAsString(report));
			var draft = call(prompt, "library_quality_insight", converter.getJsonSchema(), LibraryQualityInsight.Draft.class);
			if (draft == null || draft.headline() == null || draft.headline().isBlank()) {
				throw new IllegalStateException("empty insight");
			}
			return draft;
		} catch (Exception e) {
			log.error("Library quality insight generation failed: {}", e.getMessage());
			throw unavailable(e);
		}
	}

	public LibraryQualityInsight.Texts translate(LibraryQualityInsight.Texts texts, String language) throws QorvaException {
		try {
			var converter = new BeanOutputConverter<>(LibraryQualityInsight.Texts.class);
			var prompt = translatePrompt
				.replace("{{language_name}}", Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH))
				.replace("{{language}}", language)
				.replace("{{texts_json}}", objectMapper.writeValueAsString(texts));
			var translated = call(prompt, "library_quality_insight_translation", converter.getJsonSchema(), LibraryQualityInsight.Texts.class);
			if (translated == null || translated.recommendations() == null
				|| translated.recommendations().size() != texts.recommendations().size()) {
				throw new IllegalStateException("translation does not match the original");
			}
			return translated;
		} catch (Exception e) {
			log.warn("Library quality insight translation to {} failed: {}", language, e.getMessage());
			throw unavailable(e);
		}
	}

	private <T> T call(String prompt, String schemaName, String schema, Class<T> type) throws Exception {
		var content = chatClient.prompt()
			.options(StructuredOutput.options(model, schemaName, schema, false, StructuredOutput.temperatureFor(model, 0.2)))
			.messages(new UserMessage(prompt))
			.call()
			.content();
		return objectMapper.readValue(content, type);
	}

	private static QorvaException unavailable(Throwable cause) {
		return QorvaErrors.of(QorvaErrorCodes.QUALITY_INSIGHT_UNAVAILABLE, cause, HttpStatus.SERVICE_UNAVAILABLE);
	}

	private static String readPrompt(String file) throws QorvaException {
		try (var reader = new BufferedReader(new InputStreamReader(
			new ClassPathResource("prompts/" + file).getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n"));
		} catch (Exception e) {
			throw new QorvaException("Cannot read prompt " + file, e);
		}
	}
}
