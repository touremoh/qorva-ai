// ai/qorva/core/entity/chat/Chat.java
package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.ChatContext;
import ai.qorva.core.dto.common.ChatMetadata;
import ai.qorva.core.dto.common.Participant;
import ai.qorva.core.enums.ChatStatus;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.*;

import java.time.Instant;
import java.util.List;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Document("Chats")
@CompoundIndexes({
    @CompoundIndex(name="tenant_user_status_updated_idx", def="{'tenantId':1,'participants.userId':1,'status':1,'lastUpdatedAt':-1}"),
    @CompoundIndex(name="tenant_cv_idx", def="{'tenantId':1,'context.cvId':1}"),
    @CompoundIndex(name="tenant_job_idx", def="{'tenantId':1,'context.jobPostId':1}"),
    @CompoundIndex(name="tenant_resumeMatch_idx", def="{'tenantId':1,'context.resumeMatchId':1}")
})
public class Chat implements QorvaEntity {
    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String title;

    @Indexed
    private ChatStatus status;

    @Field("context")
    private ChatContext context;

    private List<Participant> participants;

    private ChatMetadata metadata;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;
}
