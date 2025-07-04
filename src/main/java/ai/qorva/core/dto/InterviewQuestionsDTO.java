package ai.qorva.core.dto;

import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.common.QuestionnaireDetails;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestionsDTO implements QorvaDTO {
    private String id;

    private String tenantId;
    private String jobPostId;

    private CandidateInfo candidateInfo;
    private QuestionnaireDetails questionnaireDetails;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastUpdatedAt;

    private String createdBy;
    private String lastUpdatedBy;
}
