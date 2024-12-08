package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.*;
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
@Document(collection = "CVScreeningReports")
public class CVScreeningReport implements QorvaEntity {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    private String jobTitle;

    private String candidateName;

    private SkillsMatch skillsMatch;

    private ExceedsRequirements exceedsRequirements;

    private LackingSkills lackingSkills;

    private ExperienceAlignment experienceAlignment;

    private OverallSummary overallSummary;

    private InterviewQuestions interviewQuestions;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;
}
