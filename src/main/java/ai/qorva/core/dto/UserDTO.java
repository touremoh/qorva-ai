package ai.qorva.core.dto;

import ai.qorva.core.dto.common.UserAuthority;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

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
    private String tenantId;

    @JsonProperty(access = Access.WRITE_ONLY)
    private String rawPassword;

    @JsonProperty(access = Access.WRITE_ONLY)
    private String encryptedPassword;
    private String userAccountStatus;

    @JsonProperty(access = Access.READ_ONLY)
    private String subscriptionStatus;

    List<UserAuthority> authorities;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdBy;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedBy;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastUpdatedAt;
}
