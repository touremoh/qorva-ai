package ai.qorva.core.dto;

import ai.qorva.core.dto.common.MessageMetadata;
import ai.qorva.core.dto.common.TokenUsage;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO extends AbstractQorvaDTO {
    private String id;
    private String tenantId;
    private String chatId;
    private String role; // SYSTEM/USER/ASSISTANT
    private String participantId;
    private String content;
    private TokenUsage tokens;
    private MessageMetadata metadata;
    private Instant createdAt;
}
