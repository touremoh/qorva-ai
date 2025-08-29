package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.MessageMetadata;
import ai.qorva.core.dto.common.TokenUsage;
import ai.qorva.core.enums.ChatUserRole;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.*;

import java.time.Instant;
import java.util.List;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Document("ChatMessages")
@CompoundIndex(name="tenant_chat_time_idx", def="{'tenantId':1,'chatId':1,'createdAt':1}")
public class ChatMessage implements QorvaEntity {
    @Id
    private String id;

    @Indexed
    private String tenantId;

    @Indexed
    private String chatId; // Chats._id

    @Indexed
    private ChatUserRole role;     // SYSTEM / USER / ASSISTANT

    private String participantId; // null for assistant/system

    private String content;

    private TokenUsage tokens;

    private MessageMetadata metadata;

    @CreatedDate
    private Instant createdAt;

}
