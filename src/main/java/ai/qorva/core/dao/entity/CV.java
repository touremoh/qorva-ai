package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.*;
import ai.qorva.core.dto.common.Reference;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.time.Year;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cvs")
public class CV implements QorvaEntity {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String tenantId;

    @Indexed(unique = true, sparse = true)
    private String applicantNumber;

    private String candidateProfileSummary;
    private Integer careerStartYear;

    public Integer getNbYearsOfExperience() {
        return careerStartYear == null ? null : Year.now().getValue() - careerStartYear;
    }

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
    private SalaryExpectation salaryExpectation;
    private CandidateClustering candidateClustering;
    private SearchIndex searchIndex;
    private AttachmentInfo attachment;

    /** Raw text extracted from the uploaded file, kept to allow re-running AI extraction later. */
    private String rawText;

    /** Best evidence of when the CV content was current (work history dates, document metadata, or human verification). */
    private Instant contentDate;

    /** Origin of contentDate: WORK_HISTORY | DOC_METADATA | VERIFIED | UNKNOWN. */
    private String contentDateSource;

    /** Denormalized quality defects (see QualityFlagEnum), recomputed on every write. */
    private List<String> qualityFlags;

    /** Archived CVs are excluded from quality reporting and matching. */
    private Boolean archived;

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
