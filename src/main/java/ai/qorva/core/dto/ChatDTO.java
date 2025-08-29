// ai/qorva/core/dto/chat/ChatDTO.java
package ai.qorva.core.dto;

import ai.qorva.core.dto.common.ChatContext;
import ai.qorva.core.dto.common.ChatMetadata;
import ai.qorva.core.dto.common.Participant;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatDTO extends AbstractQorvaDTO {
    private String id;
    private String tenantId;
    private String title;
    private String status;
    private ChatContext context;
    private List<Participant> participants;
    private ChatMetadata metadata;
    private Instant createdAt;
    private Instant lastUpdatedAt;
    private String createdBy;
    private String lastUpdatedBy;
}
