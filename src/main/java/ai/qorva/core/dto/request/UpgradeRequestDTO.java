package ai.qorva.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpgradeRequestDTO {

    /** Stripe price id of the plan the user chose at upgrade. */
    @NotBlank
    private String priceId;
}
