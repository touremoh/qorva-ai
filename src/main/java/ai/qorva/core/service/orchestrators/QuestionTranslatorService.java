package ai.qorva.core.service.orchestrators;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_1_MINI;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionTranslatorService {

	private final ChatClient chatClient;

	private static final String TO_ENGLISH_PROMPT = """
		If the following question is already in English, return it exactly as-is, unchanged.
		If it is in any other language, translate it to English. Preserve all proper nouns, numbers, and technical terms.
		Return only the (possibly translated) question text — no explanation, no quotes, no prefix.

		Question: %s
		""";

	private static final String MATCH_LANGUAGE_PROMPT = """
		Translate TEXT_TO_TRANSLATE into the same language as REFERENCE.
		Do NOT translate or return REFERENCE itself.
		Return only the translated TEXT_TO_TRANSLATE — no explanation, no quotes, no prefix.

		REFERENCE: %s
		TEXT_TO_TRANSLATE: %s
		""";

	public String toEnglish(String question) {
		if (question == null || question.isBlank()) {
			return question;
		}
		try {
			return chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(GPT_4_1_MINI)
					.temperature(0.0)
					.build())
				.messages(new UserMessage(TO_ENGLISH_PROMPT.formatted(question)))
				.call()
				.content();
		} catch (Exception e) {
			log.warn("Translation failed, using original question: {}", e.getMessage());
			return question;
		}
	}

	/** Translates {@code text} into the same language as {@code languageReference}. */
	public String matchLanguageOf(String text, String languageReference) {
		if (text == null || text.isBlank()) {
			return text;
		}
		try {
			return chatClient.prompt()
				.options(OpenAiChatOptions.builder()
					.model(GPT_4_1_MINI)
					.temperature(0.0)
					.build())
				.messages(new UserMessage(MATCH_LANGUAGE_PROMPT.formatted(languageReference, text)))
				.call()
				.content();
		} catch (Exception e) {
			log.warn("Language matching failed, returning original text: {}", e.getMessage());
			return text;
		}
	}
}
