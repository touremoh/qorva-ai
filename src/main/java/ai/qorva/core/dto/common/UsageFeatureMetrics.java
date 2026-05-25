package ai.qorva.core.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageFeatureMetrics {

    private Integer limit;
    private Integer consumed;
    private Long cumulative;
}
