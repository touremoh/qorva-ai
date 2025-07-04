package ai.qorva.core.dto;

import ai.qorva.core.dto.common.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.bson.types.Binary;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CVDTO extends AbstractQorvaDTO {
    private String id;
    private String tenantId;
    private String candidateProfileSummary;
    private int nbYearsOfExperience;
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
    private List<String> tags;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastUpdatedAt;

    @JsonProperty(access = Access.READ_ONLY)
    private String createdBy;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedBy;
}
