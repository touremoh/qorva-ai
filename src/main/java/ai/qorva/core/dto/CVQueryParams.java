package ai.qorva.core.dto;

import java.util.List;

public record CVQueryParams(
		List<String> skills,             // → skill dimension: keySkills, technicalSkills, softSkills, areasOfExpertise, functionalExpertise, workExperience[].toolsAndTechnologies, education[].fieldOfStudy
		List<String> roles,              // → role dimension: personalInformation.role, primaryCluster, secondaryClusters, areasOfExpertise, functionalExpertise, workExperience[].position
		List<String> industries,         // → candidateClustering.industryDomains regex (OR semantics)
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
		List<String> requiredSkills,     // → each skill is a separate AND condition (all must match)
		List<String> requiredIndustries, // → each industry is a separate AND condition (all must match)
		String clarificationQuestion,    // non-null = question too broad; skip query, ask user this instead
		List<String> applicantNumbers,   // → specific candidate refs for comparison (applicantNumber.in)
		String jobPostReference          // → specific job post ref for candidate-vs-job comparison
) {

	public static CVQueryParams empty() {
		return new CVQueryParams(
			List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
			null, null, null, null, null, null, null, List.of(), null,
			List.of(), List.of(), null, List.of(), null
		);
	}

	public CVQueryParams withoutSkills() {
		return new CVQueryParams(
			List.of(), roles, industries, languages, companies, degreeLevels, institutions,
			seniority, skillDepth, leadershipLevel, openToWork, availabilityStatus,
			location, minYearsExperience, tags, limit, List.of(), requiredIndustries, null,
			applicantNumbers, jobPostReference
		);
	}

	public boolean needsClarification() {
		return clarificationQuestion != null && !clarificationQuestion.isBlank();
	}

	public CVQueryParams withoutClarification() {
		if (!needsClarification()) {
			return this;
		}
		return new CVQueryParams(
			skills, roles, industries, languages, companies, degreeLevels, institutions,
			seniority, skillDepth, leadershipLevel, openToWork, availabilityStatus,
			location, minYearsExperience, tags, limit, requiredSkills, requiredIndustries, null,
			applicantNumbers, jobPostReference
		);
	}

	/** True when at least one search dimension carries a concrete value — {@code limit} alone does not count. */
	public boolean hasAnyFilter() {
		return notEmpty(skills) || notEmpty(roles) || notEmpty(industries) || notEmpty(languages)
			|| notEmpty(companies) || notEmpty(degreeLevels) || notEmpty(institutions)
			|| notEmpty(tags) || notEmpty(requiredSkills) || notEmpty(requiredIndustries)
			|| notEmpty(applicantNumbers)
			|| seniority != null || skillDepth != null || leadershipLevel != null
			|| openToWork != null || availabilityStatus != null || location != null
			|| minYearsExperience != null || jobPostReference != null;
	}

	/**
	 * Slot-merges this (freshly extracted) frame over the one carried from the previous turn:
	 * every value set here wins, every slot left empty here inherits the previous turn's value.
	 * This is what lets "java development" keep the {@code limit: 10} of "show me the top 10 profiles"
	 * without replaying the transcript through the extractor prompt.
	 * {@code clarificationQuestion} is never inherited — it belongs to the turn that raised it.
	 */
	public CVQueryParams mergeOnto(CVQueryParams previous) {
		if (previous == null) {
			return this;
		}
		return new CVQueryParams(
			firstNonEmpty(skills, previous.skills()),
			firstNonEmpty(roles, previous.roles()),
			firstNonEmpty(industries, previous.industries()),
			firstNonEmpty(languages, previous.languages()),
			firstNonEmpty(companies, previous.companies()),
			firstNonEmpty(degreeLevels, previous.degreeLevels()),
			firstNonEmpty(institutions, previous.institutions()),
			seniority != null ? seniority : previous.seniority(),
			skillDepth != null ? skillDepth : previous.skillDepth(),
			leadershipLevel != null ? leadershipLevel : previous.leadershipLevel(),
			openToWork != null ? openToWork : previous.openToWork(),
			availabilityStatus != null ? availabilityStatus : previous.availabilityStatus(),
			location != null ? location : previous.location(),
			minYearsExperience != null ? minYearsExperience : previous.minYearsExperience(),
			firstNonEmpty(tags, previous.tags()),
			limit != null ? limit : previous.limit(),
			firstNonEmpty(requiredSkills, previous.requiredSkills()),
			firstNonEmpty(requiredIndustries, previous.requiredIndustries()),
			clarificationQuestion,
			firstNonEmpty(applicantNumbers, previous.applicantNumbers()),
			jobPostReference != null ? jobPostReference : previous.jobPostReference()
		);
	}

	private static boolean notEmpty(List<String> values) {
		return values != null && !values.isEmpty();
	}

	private static List<String> firstNonEmpty(List<String> preferred, List<String> fallback) {
		return notEmpty(preferred) ? preferred : (fallback != null ? fallback : List.of());
	}
}
