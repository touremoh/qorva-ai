package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.common.QuestionnaireDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document("InterviewQuestions")
public class InterviewQuestions implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String jobPostId;

    private CandidateInfo candidateInfo;
    private QuestionnaireDetails questionnaireDetails;

    private Instant createdAt;
    private Instant lastUpdatedAt;
    private String createdBy;
    private String lastUpdatedBy;
}
