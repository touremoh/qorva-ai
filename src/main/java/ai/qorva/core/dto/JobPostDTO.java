package ai.qorva.core.dto;

import ai.qorva.core.dto.common.AtsRef;
import ai.qorva.core.dto.common.ScoringRules;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostDTO extends AbstractQorvaDTO {
    private String id;
    private String tenantId;

    @JsonProperty(access = Access.READ_ONLY)
    private String jobReference;

    private ScoringRules scoringRules;
    private Boolean matchingReportsNeeded;

    /*
     * Matching state, written by the matching run and the staleness sweep only. On the DTO so an edit
     * keeps them (the whole document is rewritten from this object); read-only or hidden to clients.
     */
    @JsonProperty(access = Access.READ_ONLY)
    private String matchingStaleReason;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant matchingStaleAt;
    /** When the job was last opened or closed (server-written). */
    private Instant statusChangedAt;

    @JsonProperty(access = Access.READ_ONLY)
    private Integer matchingTopN;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastMatchedAt;

    @JsonIgnore
    private Double matchingCutoffScore;

    @JsonIgnore
    private List<String> newCandidateIds;

    /**
     * Link back to the ATS record this job was imported from. Present on the DTO only so an
     * update cannot drop it: the whole document is rewritten from this object on save, so a
     * field missing here is erased in Mongo. Owned by the sync engine — JobPostService
     * overwrites whatever a client sends with what is already stored.
     */
    private AtsRef atsRef;
    private String title;
    private String description;
    private String createdBy;
    private String lastUpdatedBy;
    private String status;

    @JsonProperty(access = Access.WRITE_ONLY)
    private float[] embedding;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastUpdatedAt;

    /** How many candidates seen since the last run would enter its top N. */
    @JsonProperty(access = Access.READ_ONLY)
    public int getNewCandidateCount() {
        return newCandidateIds != null ? newCandidateIds.size() : 0;
    }

    public String toJobTitleAndDescription() {
        return "Job Title: " + getTitle() + "\n Job Description: " + getDescription();
    }
}
