package ai.qorva.core.dto.request;

import ai.qorva.core.dto.common.UserAuthority;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AddUserRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    private List<UserAuthority> authorities;

    private String communicationLanguage;
}
