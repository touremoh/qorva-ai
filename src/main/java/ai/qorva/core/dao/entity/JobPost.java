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
import java.util.List;

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

    /** When {@code status} last changed (open ↔ closed); null for jobs never opened or closed since 2026-10-08. */
    private Instant statusChangedAt;

    private ScoringRules scoringRules;
    private Boolean matchingReportsNeeded;

    /** Why the results are out of date ({@code MatchingStaleReasonEnum}); null once matched. */
    private String matchingStaleReason;

    /** When the results became out of date (or for a stronger reason) — one "needs matching" episode, for rules. */
    private Instant matchingStaleAt;

    /** Top N of the last run — the next run's default. */
    private Integer matchingTopN;

    private Instant lastMatchedAt;

    /** Similarity a new candidate must reach to enter the current top N (the N-th result, or the floor). */
    private Double matchingCutoffScore;

    /** Candidates seen since the last run that would enter its top N (capped; the count feeds the badge). */
    private List<String> newCandidateIds;

    /** Source link when this job was imported from an external ATS. */
    private ai.qorva.core.dto.common.AtsRef atsRef;

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
