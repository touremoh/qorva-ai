package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CVOutputDTO(
    @JsonProperty(required = true, value = "candidateProfileSummary") String candidateProfileSummary,
    @JsonProperty(required = true, value = "nbYearsOfExperience") int nbYearsOfExperience,
    @JsonProperty(required = true, value = "personalInformation") PersonalInformation personalInformation,
    @JsonProperty(required = true, value = "keySkills") KeySkill[] keySkills,
    @JsonProperty(required = true, value = "profiles") Profiles profiles,
    @JsonProperty(required = true, value = "workExperience") WorkExperience[] workExperience,
    @JsonProperty(required = true, value = "education") Education[] education,
    @JsonProperty(required = true, value = "certifications") Certification[] certifications,
    @JsonProperty(required = true, value = "skillsAndQualifications") SkillsAndQualifications skillsAndQualifications,
    @JsonProperty(required = true, value = "projectsAndAchievements") ProjectAndAchievement[] projectsAndAchievements,
    @JsonProperty(required = true, value = "interestsAndHobbies") String[] interestsAndHobbies,
    @JsonProperty(required = true, value = "references") Reference[] references,
    @JsonProperty(required = true, value = "tags") String[] tags) {

    public record PersonalInformation(
        @JsonProperty(required = true, value = "name") String name,
        @JsonProperty(required = true, value = "contact") Contact contact,
        @JsonProperty(required = true, value = "role") String role,
        @JsonProperty(required = true, value = "availability") Availability availability,
        @JsonProperty(required = true, value = "summary") String summary) {
    }

    public record Contact(
        @JsonProperty(required = true, value = "email") String email,
        @JsonProperty(required = true, value = "phone") String phone,
        @JsonProperty(required = true, value = "address") String address) {
    }

    public record Availability(
        @JsonProperty(required = true, value = "startDate") String startDate,
        @JsonProperty(required = true, value = "endDate") String endDate,
        @JsonProperty(required = true, value = "type") String type) {
    }

    public record KeySkill(
        @JsonProperty(required = true, value = "category") String category,
        @JsonProperty(required = true, value = "skills") String[] skills) {
    }

    public record Profiles(
        @JsonProperty(required = true, value = "areasOfExpertise") String[] areasOfExpertise,
        @JsonProperty(required = true, value = "keyResponsibilities") String[] keyResponsibilities) {
    }

    public record WorkExperience(
        @JsonProperty(required = true, value = "company") String company,
        @JsonProperty(required = true, value = "website") String website,
        @JsonProperty(required = true, value = "location") String location,
        @JsonProperty(required = true, value = "from") String from,
        @JsonProperty(required = true, value = "to") String to,
        @JsonProperty(required = true, value = "position") String position,
        @JsonProperty(required = true, value = "activities") Activity[] activities,
        @JsonProperty(required = true, value = "achievements") String[] achievements,
        @JsonProperty(required = true, value = "toolsAndTechnologies") String[] toolsAndTechnologies) {
        public record Activity(
            @JsonProperty(required = true, value = "project") String project,
            @JsonProperty(required = true, value = "tasks") String[] tasks
        ) {}
    }

    public record Education(
        @JsonProperty(required = true, value = "year") String year,
        @JsonProperty(required = true, value = "institution") String institution,
        @JsonProperty(required = true, value = "degree") String degree,
        @JsonProperty(required = true, value = "fieldOfStudy") String fieldOfStudy,
        @JsonProperty(required = true, value = "achievements") String[] achievements) {
    }

    public record Certification(
        @JsonProperty(required = true, value = "title") String title,
        @JsonProperty(required = true, value = "institution") String institution,
        @JsonProperty(required = true, value = "year") String year,
        @JsonProperty(required = true, value = "description") String description) {
    }

    public record SkillsAndQualifications(
        @JsonProperty(required = true, value = "technicalSkills") String[] technicalSkills,
        @JsonProperty(required = true, value = "softSkills") String[] softSkills,
        @JsonProperty(required = true, value = "languages") Language[] languages) {
        public record Language(
            @JsonProperty(required = true, value = "language") String language,
            @JsonProperty(required = true, value = "proficiency") Proficiency proficiency) {
            public record Proficiency(
                @JsonProperty(required = true, value = "read") String read,
                @JsonProperty(required = true, value = "written") String written,
                @JsonProperty(required = true, value = "spoken") String spoken
            ) { }
        }
    }

    public record ProjectAndAchievement(
        @JsonProperty(required = true, value = "title") String title,
        @JsonProperty(required = true, value = "description") String description,
        @JsonProperty(required = true, value = "date") String date,
        @JsonProperty(required = true, value = "impact") String impact) {
    }

    public record Reference(
        @JsonProperty(required = true, value = "name") String name,
        @JsonProperty(required = true, value = "position") String position,
        @JsonProperty(required = true, value = "company") String company,
        @JsonProperty(required = true, value = "contact") Contact contact) {
    }
}
