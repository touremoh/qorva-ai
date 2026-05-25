package ai.qorva.core.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFeatures {

    private Integer seats;
    private FeatureLimits limits;
    private OveragePricing overage;
}
