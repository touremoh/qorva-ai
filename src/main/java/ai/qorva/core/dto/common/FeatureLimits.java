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
}
