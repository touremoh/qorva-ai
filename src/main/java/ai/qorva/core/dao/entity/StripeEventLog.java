package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document("StripeEventLogs")
public class StripeEventLog implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    private String eventType;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    // TODO new field needed. E.g: payment initiation status

    @CreatedDate
    private Instant createdAt;

    @CreatedBy
    @Field(targetType = FieldType.OBJECT_ID)
    private String createdBy;
}