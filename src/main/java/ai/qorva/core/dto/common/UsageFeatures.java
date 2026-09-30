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
    /** Copilot runs, chat and background; absent on periods created before the agent shipped. */
    private UsageFeatureMetrics agentRuns;
}
