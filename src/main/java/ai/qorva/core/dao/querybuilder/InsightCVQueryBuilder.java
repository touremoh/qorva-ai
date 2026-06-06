package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dto.CVQueryParams;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds MongoDB Criteria from CVQueryParams using a dimension-based, multi-field approach.
 *
 * Each dimension is an OR across semantically related fields; all dimensions are AND'd together.
 *
 * Skill dimension  → keySkills.skills | technicalSkills | softSkills | areasOfExpertise | functionalExpertise | workExperience[].toolsAndTechnologies | education[].fieldOfStudy
 * Role  dimension  → personalInformation.role | primaryCluster | secondaryClusters | areasOfExpertise | functionalExpertise | workExperience[].position
 *
 * areasOfExpertise and functionalExpertise are intentionally included in BOTH dimensions because
 * they are hybrid fields that describe both what a candidate can do (skill) and what they are (role).
 */
@Slf4j
@Component
public class InsightCVQueryBuilder {

	// Domain qualifier words extracted from multi-word role phrases as fallback matchers.
	// Generic words (engineer, developer, specialist, etc.) are excluded to avoid false positives.
	private static final Set<String> DOMAIN_QUALIFIERS = Set.of(
		"backend", "front-end", "frontend", "fullstack", "full-stack",
		"devops", "mobile", "data", "cloud", "security", "embedded",
		"infrastructure", "platform", "ml", "ai", "fintech", "blockchain",
		"qa", "sre", "erp", "sap", "salesforce", "bi", "etl", "crm"
	);

	// Generic role words that must NOT be used alone as domain qualifiers
	private static final Set<String> ROLE_STOPWORDS = Set.of(
		"engineer", "developer", "specialist", "consultant", "expert",
		"manager", "analyst", "architect", "lead", "senior", "junior",
		"mid", "staff", "principal", "intern", "head", "director"
	);

	// Degree-level regex patterns keyed by normalized value
	private static final java.util.Map<String, String> DEGREE_PATTERNS = java.util.Map.of(
		"bachelor",  "bachelor|BSc|B\\.S\\.|B\\.A\\.|undergraduate|licenc|licens",
		"master",    "master|MSc|M\\.S\\.|M\\.A\\.|graduate|magistere",
		"phd",       "ph\\.?d\\.?|doctorat|doctora|DPhil|D\\.Phil",
		"mba",       "MBA|master of business",
		"associate", "associate|HND|HNC"
	);

	public Criteria build(ObjectId tenantId, CVQueryParams params) {
		log.debug("Building CV query for tenantId: {}, params: {}", tenantId, params);

		List<Criteria> conditions = new ArrayList<>();
		conditions.add(Criteria.where("tenantId").is(tenantId));

		if (params == null) {
			return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
		}

		// Skill and role dimensions are OR'd together: matching either is sufficient.
		// AND semantics would reject profiles with French job titles (e.g. "Développeur Node.js")
		// that satisfy the skill dimension but not the English role phrases.
		List<Criteria> techCriteria = new ArrayList<>();
		if (params.skills() != null && !params.skills().isEmpty()) {
			techCriteria.add(skillDimension(params.skills()));
		}
		if (params.roles() != null && !params.roles().isEmpty()) {
			techCriteria.add(roleDimension(params.roles()));
		}
		if (!techCriteria.isEmpty()) {
			conditions.add(techCriteria.size() == 1
				? techCriteria.get(0)
				: new Criteria().orOperator(techCriteria.toArray(new Criteria[0])));
		}

		if (params.industries() != null && !params.industries().isEmpty()) {
			List<Criteria> ic = params.industries().stream()
				.map(ind -> Criteria.where("candidateClustering.industryDomains").regex(escape(ind), "i"))
				.collect(Collectors.toList());
			conditions.add(new Criteria().orOperator(ic.toArray(new Criteria[0])));
		}

		if (params.languages() != null && !params.languages().isEmpty()) {
			// Each language is a separate AND condition — the candidate must speak all listed languages
			params.languages().forEach(lang ->
				conditions.add(Criteria.where("skillsAndQualifications.languages.language").regex(escape(lang), "i"))
			);
		}

		if (params.companies() != null && !params.companies().isEmpty()) {
			List<Criteria> cc = params.companies().stream()
				.map(c -> Criteria.where("workExperience.company").regex(escape(c), "i"))
				.collect(Collectors.toList());
			conditions.add(new Criteria().orOperator(cc.toArray(new Criteria[0])));
		}

		if (params.degreeLevels() != null && !params.degreeLevels().isEmpty()) {
			List<Criteria> dc = params.degreeLevels().stream()
				.map(d -> Criteria.where("education.degree").regex(degreePattern(d), "i"))
				.collect(Collectors.toList());
			conditions.add(new Criteria().orOperator(dc.toArray(new Criteria[0])));
		}

		if (params.institutions() != null && !params.institutions().isEmpty()) {
			List<Criteria> ic = params.institutions().stream()
				.map(inst -> Criteria.where("education.institution").regex(escape(inst), "i"))
				.collect(Collectors.toList());
			conditions.add(new Criteria().orOperator(ic.toArray(new Criteria[0])));
		}

		if (params.seniority() != null) {
			conditions.add(Criteria.where("candidateClustering.seniorityLevel").is(params.seniority()));
		}
		if (params.skillDepth() != null) {
			conditions.add(Criteria.where("candidateClustering.skillDepth").is(params.skillDepth()));
		}
		if (params.leadershipLevel() != null) {
			conditions.add(Criteria.where("candidateClustering.leadershipAndInfluence").is(params.leadershipLevel()));
		}
		if (params.openToWork() != null) {
			conditions.add(Criteria.where("personalInformation.availability.openToWork").is(params.openToWork()));
		}
		if (params.availabilityStatus() != null) {
			conditions.add(Criteria.where("personalInformation.availability.status").is(params.availabilityStatus()));
		}
		if (params.location() != null) {
			conditions.add(Criteria.where("personalInformation.contact").regex(escape(params.location()), "i"));
		}
		if (params.minYearsExperience() != null) {
			conditions.add(Criteria.where("careerStartYear").lte(Year.now().getValue() - params.minYearsExperience()));
		}
		if (params.tags() != null && !params.tags().isEmpty()) {
			conditions.add(Criteria.where("tags").in(params.tags()));
		}

		return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
	}

	/**
	 * Skill dimension: OR across all skill-bearing fields.
	 * Uses word-boundary regex so "Node.js" doesn't bleed into "Vue.js".
	 */
	private Criteria skillDimension(List<String> skills) {
		List<Criteria> perTerm = skills.stream().map(skill -> {
			String pattern = "\\b" + escape(skill) + "\\b";
			return new Criteria().orOperator(
				Criteria.where("keySkills.skills").regex(pattern, "i"),
				Criteria.where("skillsAndQualifications.technicalSkills").regex(pattern, "i"),
				Criteria.where("skillsAndQualifications.softSkills").regex(pattern, "i"),
				Criteria.where("profiles.areasOfExpertise").regex(pattern, "i"),
				Criteria.where("candidateClustering.functionalExpertise").regex(pattern, "i"),
				Criteria.where("workExperience.toolsAndTechnologies").regex(pattern, "i"),
				Criteria.where("education.fieldOfStudy").regex(pattern, "i"),
				// A technology in a job title implies the skill (e.g. "Node.js Developer" → has Node.js)
				Criteria.where("personalInformation.role").regex(pattern, "i"),
				Criteria.where("workExperience.position").regex(pattern, "i")
			);
		}).collect(Collectors.toList());
		// At least ONE skill term must match somewhere in the skill fields
		return new Criteria().orOperator(perTerm.toArray(new Criteria[0]));
	}

	/**
	 * Role dimension: OR across all role/title/cluster fields.
	 * Uses substring regex (no word boundary) — role phrases are often substrings of longer titles.
	 * Includes areasOfExpertise and functionalExpertise as hybrid fields.
	 * Includes workExperience[].position to capture career history and transitions.
	 *
	 * For multi-word role phrases, also extracts domain qualifier tokens (e.g. "backend" from
	 * "backend engineer") and searches for them with word-boundary regex. This handles titles
	 * like "Backend Node.js Developer" that don't contain the full phrase as a substring.
	 */
	private Criteria roleDimension(List<String> roles) {
		List<Criteria> perTerm = roles.stream().map(role -> {
			String pattern = escape(role);
			List<Criteria> termFields = new ArrayList<>(Arrays.asList(
				Criteria.where("personalInformation.role").regex(pattern, "i"),
				Criteria.where("candidateClustering.primaryCluster").regex(pattern, "i"),
				Criteria.where("candidateClustering.secondaryClusters").regex(pattern, "i"),
				Criteria.where("profiles.areasOfExpertise").regex(pattern, "i"),
				Criteria.where("candidateClustering.functionalExpertise").regex(pattern, "i"),
				Criteria.where("workExperience.position").regex(pattern, "i")
			));

			// For multi-word phrases, also match on domain qualifier tokens individually.
			// Covers all 6 role fields + keySkills.skills (domain tags like "Backend Development").
			List<String> words = Arrays.asList(role.toLowerCase().split("[\\s\\-]+"));
			if (words.size() > 1) {
				words.stream()
					.filter(w -> DOMAIN_QUALIFIERS.contains(w) && !ROLE_STOPWORDS.contains(w))
					.forEach(qualifier -> {
						String qp = "\\b" + escape(qualifier) + "\\b";
						termFields.add(Criteria.where("personalInformation.role").regex(qp, "i"));
						termFields.add(Criteria.where("candidateClustering.primaryCluster").regex(qp, "i"));
						termFields.add(Criteria.where("candidateClustering.secondaryClusters").regex(qp, "i"));
						termFields.add(Criteria.where("profiles.areasOfExpertise").regex(qp, "i"));
						termFields.add(Criteria.where("candidateClustering.functionalExpertise").regex(qp, "i"));
						termFields.add(Criteria.where("workExperience.position").regex(qp, "i"));
						termFields.add(Criteria.where("keySkills.skills").regex(qp, "i"));
					});
			}

			return new Criteria().orOperator(termFields.toArray(new Criteria[0]));
		}).collect(Collectors.toList());
		return new Criteria().orOperator(perTerm.toArray(new Criteria[0]));
	}

	/** Escapes MongoDB regex metacharacters in a literal search term. */
	static String escape(String term) {
		return term.replaceAll("([.+*?^${}()|\\[\\]\\\\])", "\\\\$1");
	}

	private static String degreePattern(String normalized) {
		String pattern = DEGREE_PATTERNS.get(normalized.toLowerCase());
		return pattern != null ? pattern : escape(normalized);
	}
}
