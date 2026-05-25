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
public class ScoringRules implements Serializable {
    private List<SkillRequirement> skills;
    private ExperienceRequirements experienceRequirements;
    private LocationPreferences locationPreferences;
    private IndustryPreferences industryPreferences;
    private ScoringWeight scoringWeight;
    private Boolean filterOpenToWork;
    private List<String> availabilityStatuses;
}
