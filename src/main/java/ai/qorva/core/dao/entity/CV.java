package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.*;
import ai.qorva.core.dto.common.Reference;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.Binary;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "CVs")
public class CV implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;
    private String candidateProfileSummary;
    private Integer nbYearsOfExperience;

    @TextIndexed(weight = 5)
    private PersonalInformation personalInformation;
    private List<KeySkill> keySkills;
    private Profiles profiles;
    private List<WorkExperience> workExperience;
    private List<Education> education;
    private List<Certification> certifications;
    private SkillsAndQualifications skillsAndQualifications;
    private List<ProjectAndAchievement> projectsAndAchievements;
    private List<String> interestsAndHobbies;
    private List<Reference> references;
    private Binary attachment;

    private float[] embedding;

    @Field("score")
    private Double score;

    @TextIndexed
    private List<String> tags;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastUpdatedBy;
}
