package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CVScreeningReportOutputDTO(
    @JsonProperty(required = true, value = "skillsMatch") SkillsMatch skillsMatch,
    @JsonProperty(required = true, value = "experienceMatch") ExperienceMatch experienceMatch,
    @JsonProperty(required = true, value = "locationMatch") LocationMatch locationMatch,
    @JsonProperty(required = true, value = "industryMatch") IndustryMatch industryMatch,
    @JsonProperty(required = true, value = "missingSkills") MissingSkills missingSkills,
    @JsonProperty(required = true, value = "finalScore") FinalScore finalScore) {

    public record SkillsMatch(
        @JsonProperty(required = true, value = "score") double score,
        @JsonProperty(required = true, value = "scoreExplanation") String scoreExplanation,
        @JsonProperty(required = true, value = "matchingSkills") String[] matchingSkills
    ) {}

    public record ExperienceMatch(
        @JsonProperty(required = true, value = "score") double score,
        @JsonProperty(required = true, value = "scoreExplanation") String scoreExplanation
    ) {}

    public record LocationMatch(
        @JsonProperty(required = true, value = "score") double score,
        @JsonProperty(required = true, value = "scoreExplanation") String scoreExplanation
    ) {}

    public record IndustryMatch(
        @JsonProperty(required = true, value = "score") double score,
        @JsonProperty(required = true, value = "scoreExplanation") String scoreExplanation
    ) {}

    public record MissingSkills(
        @JsonProperty(required = true, value = "summary") String summary,
        @JsonProperty(required = true, value = "skills") SkillEntry[] skills
    ) {
        public record SkillEntry(
            @JsonProperty(required = true, value = "skill") String skill,
            @JsonProperty(required = true, value = "importance") String importance
        ) {}
    }

    public record FinalScore(
        @JsonProperty(required = true, value = "score") double score,
        @JsonProperty(required = true, value = "scoreExplanation") String scoreExplanation
    ) {}
}
