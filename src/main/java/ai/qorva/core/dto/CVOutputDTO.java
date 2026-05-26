package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CVOutputDTO(
    @JsonProperty("candidateProfileSummary") String candidateProfileSummary,
    @JsonProperty("careerStartYear") int careerStartYear,
    @JsonProperty("personalInformation") PersonalInformation personalInformation,
    @JsonProperty("keySkills") KeySkill[] keySkills,
    @JsonProperty("profiles") Profiles profiles,
    @JsonProperty("workExperience") WorkExperience[] workExperience,
    @JsonProperty("education") Education[] education,
    @JsonProperty("certifications") Certification[] certifications,
    @JsonProperty("skillsAndQualifications") SkillsAndQualifications skillsAndQualifications,
    @JsonProperty("projectsAndAchievements") ProjectAndAchievement[] projectsAndAchievements,
    @JsonProperty("interestsAndHobbies") String[] interestsAndHobbies,
    @JsonProperty("references") Reference[] references,
    @JsonProperty("tags") String[] tags,
    @JsonProperty("salaryExpectation") SalaryExpectation salaryExpectation,
    @JsonProperty("candidateClustering") CandidateClustering candidateClustering) {

    public record PersonalInformation(
        @JsonProperty("name") String name,
        @JsonProperty("contact") Contact contact,
        @JsonProperty("role") String role,
        @JsonProperty("availability") Availability availability,
        @JsonProperty("summary") String summary) {
    }

    public record Contact(
        @JsonProperty("email") String email,
        @JsonProperty("phone") String phone,
        @JsonProperty("address") String address) {
    }

    public record Availability(
        @JsonProperty("openToWork") Boolean openToWork,
        @JsonProperty("status") String status,
        @JsonProperty("availableFrom") String availableFrom,
        @JsonProperty("noticePeriodDays") Integer noticePeriodDays,
        @JsonProperty("interviewAvailability") String[] interviewAvailability,
        @JsonProperty("preferredWorkTypes") String[] preferredWorkTypes,
        @JsonProperty("preferredContractTypes") String[] preferredContractTypes,
        @JsonProperty("willingToRelocate") Boolean willingToRelocate,
        @JsonProperty("remoteOnly") Boolean remoteOnly) {
    }

    public record SalaryExpectation(
        @JsonProperty("currency") String currency,
        @JsonProperty("min") Integer min,
        @JsonProperty("max") Integer max) {
    }

    public record KeySkill(
        @JsonProperty("category") String category,
        @JsonProperty("skills") String[] skills) {
    }

    public record Profiles(
        @JsonProperty("areasOfExpertise") String[] areasOfExpertise,
        @JsonProperty("keyResponsibilities") String[] keyResponsibilities) {
    }

    public record WorkExperience(
        @JsonProperty("company") String company,
        @JsonProperty("website") String website,
        @JsonProperty("location") String location,
        @JsonProperty("from") String from,
        @JsonProperty("to") String to,
        @JsonProperty("position") String position,
        @JsonProperty("activities") Activity[] activities,
        @JsonProperty("achievements") String[] achievements,
        @JsonProperty("toolsAndTechnologies") String[] toolsAndTechnologies) {
        public record Activity(
            @JsonProperty("project") String project,
            @JsonProperty("tasks") String[] tasks
        ) {}
    }

    public record Education(
        @JsonProperty("year") String year,
        @JsonProperty("institution") String institution,
        @JsonProperty("degree") String degree,
        @JsonProperty("fieldOfStudy") String fieldOfStudy,
        @JsonProperty("achievements") String[] achievements) {
    }

    public record Certification(
        @JsonProperty("title") String title,
        @JsonProperty("institution") String institution,
        @JsonProperty("year") String year,
        @JsonProperty("description") String description) {
    }

    public record SkillsAndQualifications(
        @JsonProperty("technicalSkills") String[] technicalSkills,
        @JsonProperty("softSkills") String[] softSkills,
        @JsonProperty("languages") Language[] languages) {
        public record Language(
            @JsonProperty("language") String language,
            @JsonProperty("proficiency") String proficiency) {
        }
    }

    public record ProjectAndAchievement(
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("date") String date,
        @JsonProperty("impact") String impact) {
    }

    public record Reference(
        @JsonProperty("name") String name,
        @JsonProperty("position") String position,
        @JsonProperty("company") String company,
        @JsonProperty("contact") Contact contact) {
    }

    public record CandidateClustering(
        @JsonProperty("primaryCluster") String primaryCluster,
        @JsonProperty("secondaryClusters") String[] secondaryClusters,
        @JsonProperty("functionalExpertise") String[] functionalExpertise,
        @JsonProperty("skillDepth") String skillDepth,
        @JsonProperty("businessImpact") String[] businessImpact,
        @JsonProperty("industryDomains") String[] industryDomains,
        @JsonProperty("environmentFit") String[] environmentFit,
        @JsonProperty("seniorityLevel") String seniorityLevel,
        @JsonProperty("leadershipAndInfluence") String leadershipAndInfluence,
        @JsonProperty("learningVelocity") String learningVelocity,
        @JsonProperty("clusterConfidenceScore") Double clusterConfidenceScore,
        @JsonProperty("clusterReasoning") String clusterReasoning) {
    }
}
