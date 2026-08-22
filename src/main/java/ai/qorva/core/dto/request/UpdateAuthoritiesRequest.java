package ai.qorva.core.dto.request;

import ai.qorva.core.dto.common.UserAuthority;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateAuthoritiesRequest {

    @NotNull
    private List<UserAuthority> authorities;
}
