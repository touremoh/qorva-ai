package ai.qorva.core.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {
    private String userId;
    private Role role;
    public enum Role { OWNER, COLLABORATOR, VIEWER }
}