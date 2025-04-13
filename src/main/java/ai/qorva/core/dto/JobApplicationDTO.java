package ai.qorva.core.dto;

import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.common.AIAnalysisReportDetails;
import lombok.*;
import lombok.experimental.SuperBuilder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import java.time.Instant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplicationDTO extends AbstractQorvaDTO {
    private String id;
    private String jobPostId;
    private CandidateInfo candidateInfo;
    private String tenantId;
    private AIAnalysisReportDetails reportDetails;
    private String status;

    @JsonProperty(access = Access.READ_ONLY)
    private Instant createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    private Instant lastUpdatedAt;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdBy;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedBy;
}
