package ai.qorva.core.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
public class QorvaPromptContextHolder implements Serializable {
	private String cvContentExtractionPromptTemplate;
	private String cvOutputFormat;
	private String reportGenerationPrompt;
	private String reportOutputFormat;
}
