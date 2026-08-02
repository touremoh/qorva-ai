package ai.qorva.core.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureLimits {

    private Integer screeningActions;
    private Integer aiResumeChats;
    private Integer talentIntelligenceQueries;

    /** Max saved candidate-update invitation templates per workspace (static cap, not consumption). */
    private Integer emailTemplates;
}
