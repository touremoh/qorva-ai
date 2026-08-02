package ai.qorva.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response for demo account creation. No checkout URL — the frontend shows a success page
 * telling the user to check their email for the set-password link.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DemoRegistrationResponseDTO {
    private boolean success;
    private String email;
    private String tenantId;
    private String userId;
}
