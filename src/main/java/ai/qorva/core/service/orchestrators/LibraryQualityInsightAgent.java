package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.LibraryQualityInsight;
import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Writes the Library Quality summary in English; {@link InsightTranslationAgent} carries it into
 * other languages.
 */
@Slf4j
@Service
public class LibraryQualityInsightAgent {

	private final ChatClient chatClient;
	private final ObjectMapper objectMapper;
	private final String prompt;

	/** Short prose over a small aggregate — the mini tier is plenty; unmetered like the outreach drafts. */
	@Value("${qorva.ai.library-quality-insight.model:gpt-4.1-mini}")
	private String model;

	public LibraryQualityInsightAgent(ChatClient chatClient, ObjectMapper objectMapper) throws QorvaException {
		this.chatClient = chatClient;
		this.objectMapper = objectMapper;
		this.prompt = InsightSupport.readPrompt("Library_quality_insight_prompt.md");
	}

	public LibraryQualityInsight.Draft generate(LibraryQualityReport report) throws QorvaException {
		try {
			var rendered = prompt.replace("{{report_json}}", objectMapper.writeValueAsString(report));
			var draft = InsightSupport.call(chatClient, objectMapper, model, rendered, "library_quality_insight",
				LibraryQualityInsight.Draft.class);
			if (draft == null || draft.headline() == null || draft.headline().isBlank()) {
				throw new IllegalStateException("empty insight");
			}
			return draft;
		} catch (Exception e) {
			log.error("Library quality insight generation failed: {}", e.getMessage());
			throw InsightSupport.unavailable(QorvaErrorCodes.QUALITY_INSIGHT_UNAVAILABLE, e);
		}
	}
}
