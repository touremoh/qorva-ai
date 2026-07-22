package ai.qorva.core.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendSetPasswordRequest {

    @NotBlank
    @Email
    private String email;
}
