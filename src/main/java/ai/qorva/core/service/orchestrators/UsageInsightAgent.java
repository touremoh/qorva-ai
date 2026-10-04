package ai.qorva.core.service.orchestrators;

import org.springframework.beans.factory.annotation.Qualifier;

import ai.qorva.core.dto.UsageInsight;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Writes the Usage Monitoring summary in English; {@link InsightTranslationAgent} carries it into
 * other languages.
 */
@Slf4j
@Service
public class UsageInsightAgent {

	private final ChatClient chatClient;
	private final ObjectMapper objectMapper;
	private final String prompt;

	/** Short prose over a handful of numbers — the mini tier is plenty; unmetered. */
	@Value("${qorva.ai.usage-insight.model:gpt-4.1-mini}")
	private String model;

	public UsageInsightAgent(@Qualifier("interactiveChatClient") ChatClient chatClient, ObjectMapper objectMapper) throws QorvaException {
		this.chatClient = chatClient;
		this.objectMapper = objectMapper;
		this.prompt = InsightSupport.readPrompt("Usage_insight_prompt.md");
	}

	public UsageInsight.Draft generate(UsageInsight.Input input) throws QorvaException {
		try {
			var rendered = prompt.replace("{{usage_json}}", objectMapper.writeValueAsString(input));
			var draft = InsightSupport.call(chatClient, objectMapper, model, rendered, "usage_insight", UsageInsight.Draft.class);
			if (draft == null || draft.headline() == null || draft.headline().isBlank()) {
				throw new IllegalStateException("empty insight");
			}
			return draft;
		} catch (Exception e) {
			log.error("Usage insight generation failed: {}", e.getMessage());
			throw InsightSupport.unavailable(QorvaErrorCodes.USAGE_INSIGHT_UNAVAILABLE, e);
		}
	}
}
