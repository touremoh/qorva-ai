package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MatchingReportResponse(
    @JsonProperty(required = true, value = "skillsMatch") SkillsMatch skillsMatch,
    @JsonProperty(required = true, value = "experienceMatch") ExperienceMatch experienceMatch,
    @JsonProperty(required = true, value = "locationMatch") LocationMatch locationMatch,
    @JsonProperty(required = true, value = "industryMatch") IndustryMatch industryMatch,
    @JsonProperty(required = true, value = "missingSkills") MissingSkills missingSkills,
    @JsonProperty(required = true, value = "decisionSummary") DecisionSummary decisionSummary,
    @JsonProperty(required = true, value = "strengths") Strength[] strengths,
    @JsonProperty(required = true, value = "weaknesses") Weakness[] weaknesses,
    @JsonProperty(required = true, value = "redFlags") RedFlag[] redFlags) {

    public record SkillsMatch(
        @JsonProperty(required = true, value = "score") double score,
        @JsonProperty(required = true, value = "scoreSummary") String scoreSummary,
        @JsonProperty(required = true, value = "matchingSkills") String[] matchingSkills
    ) {}

    public record ExperienceMatch(
        @JsonProperty(required = true, value = "score") double score,
        @JsonProperty(required = true, value = "scoreSummary") String scoreSummary
    ) {}

    public record LocationMatch(
        @JsonProperty(required = true, value = "score") double score,
        @JsonProperty(required = true, value = "scoreSummary") String scoreSummary
    ) {}

    public record IndustryMatch(
        @JsonProperty(required = true, value = "score") double score,
        @JsonProperty(required = true, value = "scoreSummary") String scoreSummary
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

    public record DecisionSummary(
        @JsonProperty(required = true, value = "reportHeadline") String reportHeadline,
        @JsonProperty(required = true, value = "finalScore") double finalScore,
        @JsonProperty(required = true, value = "detailedSummary") String detailedSummary,
        @JsonProperty(required = true, value = "shortVerdict") String shortVerdict,
        @JsonProperty(required = true, value = "recommendation") String recommendation,
        @JsonProperty(required = true, value = "confidenceLevel") String confidenceLevel
    ) {}

    public record Strength(
        @JsonProperty(required = true, value = "title") String title,
        @JsonProperty(required = true, value = "evidence") String evidence,
        @JsonProperty(required = true, value = "importance") String importance
    ) {}

    public record Weakness(
        @JsonProperty(required = true, value = "title") String title,
        @JsonProperty(required = true, value = "evidence") String evidence,
        @JsonProperty(required = true, value = "severity") String severity
    ) {}

    public record RedFlag(
        @JsonProperty(required = true, value = "title") String title,
        @JsonProperty(required = true, value = "evidence") String evidence,
        @JsonProperty(required = true, value = "severity") String severity,
        @JsonProperty(required = true, value = "suggestedInterviewQuestion") String suggestedInterviewQuestion
    ) {}
}
