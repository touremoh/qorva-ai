package ai.qorva.core.service.orchestrators;

import org.springframework.beans.factory.annotation.Qualifier;

import ai.qorva.core.dto.InsightTexts;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Carries an English AI summary into another language without changing its content, so every
 * user of a tenant gets the same advice whatever their language. Shared by every AI summary.
 */
@Slf4j
@Service
public class InsightTranslationAgent {

	private final ChatClient chatClient;
	private final ObjectMapper objectMapper;
	private final String prompt;

	@Value("${qorva.ai.insight-translation.model:gpt-4.1-mini}")
	private String model;

	public InsightTranslationAgent(@Qualifier("interactiveChatClient") ChatClient chatClient, ObjectMapper objectMapper) throws QorvaException {
		this.chatClient = chatClient;
		this.objectMapper = objectMapper;
		this.prompt = InsightSupport.readPrompt("Insight_translate_prompt.md");
	}

	/**
	 * @param unavailableKey the caller's error key, thrown (503) when the model cannot translate
	 */
	/**
	 * @param unavailableKey the caller's error key, thrown (503) when the model cannot translate
	 */
	public InsightTexts translate(InsightTexts texts, String language, String unavailableKey) throws QorvaException {
		try {
			var rendered = prompt
				.replace("{{language_name}}", Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH))
				.replace("{{language}}", language)
				.replace("{{texts_json}}", objectMapper.writeValueAsString(texts));
			var translated = InsightSupport.call(chatClient, objectMapper, model, rendered, "insight_translation", InsightTexts.class);
			if (translated == null || translated.recommendations() == null
				|| translated.recommendations().size() != texts.recommendations().size()) {
				throw new IllegalStateException("translation does not match the original");
			}
			return translated;
		} catch (Exception e) {
			log.warn("AI summary translation to {} failed: {}", language, e.getMessage());
			throw InsightSupport.unavailable(unavailableKey, e);
		}
	}
}
