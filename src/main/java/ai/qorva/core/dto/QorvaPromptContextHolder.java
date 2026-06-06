package ai.qorva.core.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
@Builder
public class QorvaPromptContextHolder implements Serializable {
	private String cvContentExtractionPromptTemplate;
	private String cvOutputFormat;
	private String reportGenerationPrompt;
	private String reportOutputFormat;
	private String intentClassifierPrompt;
	private Map<InsightIntent, String> entityExtractorPrompts;
	private String insightAnswerGeneratorPrompt;

	public String getEntityExtractorPrompt(InsightIntent intent) {
		if (entityExtractorPrompts == null) return "";
		return entityExtractorPrompts.getOrDefault(intent, "");
	}
}
