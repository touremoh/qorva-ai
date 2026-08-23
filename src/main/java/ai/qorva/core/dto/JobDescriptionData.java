package ai.qorva.core.dto;

import ai.qorva.core.dto.common.ScoringRules;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** API shapes for the AI job-description builder ({@code POST /jobs/description/generate}). */
public final class JobDescriptionData {

	private JobDescriptionData() {}

	@Getter
	@Setter
	public static class GenerateRequest {

		@NotBlank
		private String title;

		private String seniority;
		private String mustHaveSkills;
		private String niceToHaveSkills;
		private String location;
		private String contractType;
		private String tone;
		/** Output language code (e.g. "en"); defaults to the Accept-Language header. */
		private String language;
		private String extraNotes;
	}

	/** LLM output shape — kept as a mutable bean for the structured-output converter. */
	@Getter
	@Setter
	@NoArgsConstructor
	public static class Draft {
		private String title;
		private String description;
	}

	/** Draft plus suggested scoring rules, so one click prepares the whole job post. */
	public record GenerateResponse(String title, String description, ScoringRules scoringRules) {}
}
