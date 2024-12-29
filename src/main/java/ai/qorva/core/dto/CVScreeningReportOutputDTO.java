package ai.qorva.core.dto;

import ai.qorva.core.dto.common.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CVScreeningReportOutputDTO(
    @JsonProperty(required = true, value = "jobTitle") String jobTitle,
    @JsonProperty(required = true, value = "candidateName") String candidateName,
    @JsonProperty(required = true, value = "skillsMatch") SkillsMatch skillsMatch,
    @JsonProperty(required = true, value = "exceedsRequirements") ExceedsRequirements exceedsRequirements,
    @JsonProperty(required = true, value = "lackingSkills") LackingSkills lackingSkills,
    @JsonProperty(required = true, value = "experienceAlignment") ExperienceAlignment experienceAlignment,
    @JsonProperty(required = true, value = "overallSummary") OverallSummary overallSummary,
    @JsonProperty(required = true, value = "interviewQuestions") InterviewQuestions interviewQuestions) {
	public record SkillsMatch(
		@JsonProperty(required = true, value = "summary") String summary,
		@JsonProperty(required = true, value = "degreeOfMatch") int degreeOfMatch
	) {}

	public record ExceedsRequirements(
		@JsonProperty(required = true, value = "summary") String summary
	) {}

	public record LackingSkills(
		@JsonProperty(required = true, value = "summary") String summary
	) {}

	public record ExperienceAlignment(
		@JsonProperty(required = true, value = "summary") String summary
	) {}

	public record OverallSummary(
		@JsonProperty(required = true, value = "summary") String summary,
		@JsonProperty(required = true, value = "score") int score,
		@JsonProperty(required = true, value = "pointsForImprovement") String[] pointsForImprovement
	) {}

	public record InterviewQuestions(
		@JsonProperty(required = true, value = "skillsBasedQuestions") String[] skillsBasedQuestions,
		@JsonProperty(required = true, value = "strengthBasedQuestions") String[] strengthBasedQuestions,
		@JsonProperty(required = true, value = "gapExplorationQuestions") String[] gapExplorationQuestions
	) {}
}






