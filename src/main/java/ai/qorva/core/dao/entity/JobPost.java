package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.ScoringRules;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "job_posts")
public class JobPost implements QorvaEntity {

    @Id
    private String id;

    private String jobReference;
    private String title;
    private String description;

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;
    private String status;

    private ScoringRules scoringRules;
    private Boolean matchingReportsNeeded;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;

    private float[] embedding;
}
