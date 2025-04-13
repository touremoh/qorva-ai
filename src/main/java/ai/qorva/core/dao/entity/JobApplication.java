package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.AIAnalysisReportDetails;
import ai.qorva.core.dto.common.CandidateInfo;
import lombok.*;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "JobApplications")
public class JobApplication implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String jobPostId;

    private CandidateInfo candidateInfo;

    private String tenantId;

    private AIAnalysisReportDetails aiAnalysisReportDetails;

    private String status;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    @Field(targetType = FieldType.OBJECT_ID)
    private String createdBy;

    @LastModifiedBy
    @Field(targetType = FieldType.OBJECT_ID)
    private String lastUpdatedBy;
}
