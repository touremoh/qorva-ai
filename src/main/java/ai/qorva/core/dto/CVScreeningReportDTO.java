package ai.qorva.core.dto;

import ai.qorva.core.dto.common.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class CVScreeningReportDTO extends AbstractQorvaDTO {
    private String id;
    private String companyId;
    private String jobTitle;
    private String candidateName;
    private SkillsMatch skillsMatch;
    private ExceedsRequirements exceedsRequirements;
    private LackingSkills lackingSkills;
    private ExperienceAlignment experienceAlignment;
    private OverallSummary overallSummary;
    private InterviewQuestions interviewQuestions;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedAt;
}
