package ai.qorva.core.dto;

import java.util.List;

public record CVQueryParams(
		List<String> skills,             // → skill dimension: keySkills, technicalSkills, softSkills, areasOfExpertise, functionalExpertise, workExperience[].toolsAndTechnologies, education[].fieldOfStudy
		List<String> roles,              // → role dimension: personalInformation.role, primaryCluster, secondaryClusters, areasOfExpertise, functionalExpertise, workExperience[].position
		List<String> industries,         // → candidateClustering.industryDomains regex
		List<String> languages,          // → skillsAndQualifications.languages[].language regex
		List<String> companies,          // → workExperience[].company regex
		List<String> degreeLevels,       // → education[].degree (normalized: bachelor|master|phd|mba|associate)
		List<String> institutions,       // → education[].institution regex
		String seniority,                // → candidateClustering.seniorityLevel exact
		String skillDepth,               // → candidateClustering.skillDepth exact
		String leadershipLevel,          // → candidateClustering.leadershipAndInfluence exact
		Boolean openToWork,              // → personalInformation.availability.openToWork exact
		String availabilityStatus,       // → personalInformation.availability.status exact
		String location,                 // → personalInformation.contact regex
		Integer minYearsExperience,      // → careerStartYear lte
		List<String> tags,               // → tags.in
		Integer limit,
		String clarificationQuestion     // non-null = question too broad; skip query, ask user this instead
) {

	public static CVQueryParams empty() {
		return new CVQueryParams(
			List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
			null, null, null, null, null, null, null, List.of(), null, null
		);
	}

	public CVQueryParams withoutSkills() {
		return new CVQueryParams(
			List.of(), roles, industries, languages, companies, degreeLevels, institutions,
			seniority, skillDepth, leadershipLevel, openToWork, availabilityStatus,
			location, minYearsExperience, tags, limit, null
		);
	}

	public boolean needsClarification() {
		return clarificationQuestion != null && !clarificationQuestion.isBlank();
	}
}
