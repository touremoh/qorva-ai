package ai.qorva.core.dao.entity;

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
@Document(collection = "DemoRequestors")
public class DemoRequestor implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    private String email;
    private String firstName;
    private String lastName;
    private String organizationName;
    private Double organizationSize;
    private Double nbApplicationsPerMonth;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;
}
