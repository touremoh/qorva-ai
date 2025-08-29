package ai.qorva.core.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostUserMessageRequest {
    private String tenantId;
    private String username;
    private String content;
}
