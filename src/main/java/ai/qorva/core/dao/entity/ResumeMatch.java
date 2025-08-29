package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.AIAnalysisReportDetails;
import ai.qorva.core.dto.common.CandidateInfo;
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
@Document(collection = "ResumeMatches")
public class ResumeMatch implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String jobPostId;

    private String jobPostTitle;

    private CandidateInfo candidateInfo;

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    private AIAnalysisReportDetails aiAnalysisReportDetails;

    private String status;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;
}
