package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.MatchingReportDetails;
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
@Document(collection = "matching_reports")
public class MatchingReport implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String jobPostId;

    private String jobPostTitle;

    private CandidateInfo candidateInfo;

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    private MatchingReportDetails matchingReportDetails;

    private String status;

    /** True once the candidate left the job's latest results; the report stays until the recruiter deletes it. */
    private Boolean outdated;

    /** {@code MatchingOutdatedReasonEnum} name. */
    private String outdatedReason;

    private Instant outdatedAt;

    /** The score before the last re-scoring, so the list can show what moved. */
    private Double previousFinalScore;

    private Instant rescoredAt;

    /** Hash of everything the report was generated from — an equal hash means it can be reused for free. */
    private String inputFingerprint;

    /** Hash of the candidate's side alone, to tell when an edit made this report stale. */
    private String cvFingerprint;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;
}
