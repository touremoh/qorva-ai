package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisReportDetails implements Serializable {
    private String detailsID;
    private SkillsMatch skillsMatch;
    private ExperienceMatch experienceMatch;
    private LocationMatch locationMatch;
    private IndustryMatch industryMatch;
    private MissingSkills missingSkills;
    private FinalScore finalScore;
}
