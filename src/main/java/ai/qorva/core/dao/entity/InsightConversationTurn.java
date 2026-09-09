package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.CVQueryParams;
import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.InsightResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "insight_conversation_turns")
@CompoundIndexes({
    @CompoundIndex(name = "conversation_tenant_user_idx", def = "{'conversationId': 1, 'tenantId': 1, 'initiatedBy': 1}"),
    @CompoundIndex(name = "tenant_user_created_idx", def = "{'tenantId': 1, 'initiatedBy': 1, 'createdAt': 1}")
})
public class InsightConversationTurn implements QorvaEntity {

    @Id
    private String id;

    private String conversationId;
    private String title;        // set on the first turn only; null on subsequent turns

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    private String initiatedBy;  // email of the user who asked the question

    private String question;
    private String englishQuestion;   // the question after translation and follow-up resolution; replayed as the next turn's context
    private InsightIntent intent;
    private CVQueryParams queryParams; // filters resolved for this turn; merged under the next turn's own extraction
    private boolean awaitingClarification; // this turn asked the user for specifics — the next utterance answers it
    private InsightResponseDTO response;

    @CreatedDate
    private Instant createdAt;
}
