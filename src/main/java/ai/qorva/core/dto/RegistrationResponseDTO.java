package ai.qorva.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationResponseDTO {
    private String checkoutUrl;
    private String tenantId;
    private String userId;
}
