package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchingReportDetails implements Serializable {
    private String detailsID;
    private SkillsMatch skillsMatch;
    private ExperienceMatch experienceMatch;
    private LocationMatch locationMatch;
    private IndustryMatch industryMatch;
    private MissingSkills missingSkills;
    private DecisionSummary decisionSummary;
    private List<Strength> strengths;
    private List<Weakness> weaknesses;
    private List<RedFlag> redFlags;
}
