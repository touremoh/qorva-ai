package ai.qorva.core.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OveragePricing {

    private boolean enabled;
    private Double screeningActionPrice;
    private Double chatPrice;
    private Double intelligenceQueryPrice;
}
