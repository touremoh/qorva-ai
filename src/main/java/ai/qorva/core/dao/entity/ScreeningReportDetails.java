package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ScreeningReportDetails")
public class ScreeningReportDetails implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String companyId;
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
