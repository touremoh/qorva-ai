package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionRequestDTO {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String tenantId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String userId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String priceId;
}
