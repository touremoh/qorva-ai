package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.common.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.Binary;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

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

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant lastUpdatedAt;
}
