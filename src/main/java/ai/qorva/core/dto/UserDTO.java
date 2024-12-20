package ai.qorva.core.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO extends AbstractQorvaDTO {
    private String id;
    private String firstName;
    private String lastName;
    private String email;

    @JsonProperty(access = Access.WRITE_ONLY)
    private String rawPassword;

    @JsonProperty(access = Access.WRITE_ONLY)
    private String encryptedPassword;

    private String accountStatus;
    private String companyId;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedAt;
}
