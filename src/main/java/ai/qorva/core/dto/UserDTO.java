package ai.qorva.core.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO extends AbstractQorvaDTO {
    private String firstName;
    private String lastName;
    private String email;
    private String accountStatus;
    private String companyId;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedAt;
}
