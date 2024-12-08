package ai.qorva.core.dao.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "JobsPosts")
public class JobPost implements QorvaEntity {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    private String title;

    private String description;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @MongoId(FieldType.OBJECT_ID)
    private String createdBy;

    @MongoId(FieldType.OBJECT_ID)
    private String lastUpdatedBy;
}
