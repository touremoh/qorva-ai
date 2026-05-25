package ai.qorva.core.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageFeatures {

    private UsageFeatureMetrics screeningActions;
    private UsageFeatureMetrics aiResumeChats;
    private UsageFeatureMetrics talentIntelligenceQueries;
}
