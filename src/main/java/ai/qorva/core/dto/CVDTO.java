package ai.qorva.core.dto;

import ai.qorva.core.dto.common.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.experimental.SuperBuilder;
import org.bson.types.Binary;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CVDTO extends AbstractQorvaDTO {
    private String id;
    private String companyId;
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
    private String createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    private String lastUpdatedAt;
}
