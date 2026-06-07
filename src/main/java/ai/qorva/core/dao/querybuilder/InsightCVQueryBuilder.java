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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds MongoDB Criteria from CVQueryParams using a dimension-based, multi-field approach.
 *
 * Each dimension is an OR across semantically related fields; all dimensions are AND'd together.
 * All text matching targets the English-normalized searchIndex fields, ensuring correct behavior
 * for multilingual CVs regardless of the CV's original language.
 *
 * Skill dimension  → searchIndex.skills | searchIndex.roles (technology in a role title implies the skill)
 * Role  dimension  → searchIndex.roles
 * Industry filter  → searchIndex.industries
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

	// Maps umbrella industry terms (lowercase) to their stored-level sector labels.
	// Used to expand both `industries` (OR across all variants) and each entry of
	// `requiredIndustries` (OR within one required sector group, AND'd across groups).
	private static final Map<String, List<String>> INDUSTRY_EXPANSION = Map.ofEntries(
		Map.entry("financial services", List.of("Fintech", "FinTech", "Banking", "Insurance", "Finance", "Financial Services", "Financial Technology")),
		Map.entry("finance",            List.of("Fintech", "FinTech", "Banking", "Insurance", "Finance", "Financial Services", "Financial Technology")),
		Map.entry("healthcare",         List.of("Healthcare", "Health", "Medical", "Pharma", "Pharmaceutical", "Bioinformatics", "Life Sciences")),
		Map.entry("health",             List.of("Healthcare", "Health", "Medical", "Pharma", "Pharmaceutical")),
		Map.entry("retail",             List.of("Retail", "E-Commerce", "eCommerce", "Consumer Goods")),
		Map.entry("e-commerce",         List.of("E-Commerce", "eCommerce", "Retail", "Consumer Goods")),
		Map.entry("energy",             List.of("Energy", "Oil & Gas", "Renewables", "Utilities", "Clean Energy")),
		Map.entry("manufacturing",      List.of("Manufacturing", "Industrial", "Automotive", "Aerospace")),
		Map.entry("media",              List.of("Media", "Entertainment", "Gaming", "Publishing", "Broadcasting")),
		Map.entry("entertainment",      List.of("Entertainment", "Media", "Gaming", "Publishing")),
		Map.entry("logistics",          List.of("Logistics", "Supply Chain", "Transportation", "Shipping")),
		Map.entry("supply chain",       List.of("Supply Chain", "Logistics", "Transportation", "Shipping")),
		Map.entry("education",          List.of("Education", "EdTech", "E-Learning", "Academia")),
		Map.entry("telecommunications", List.of("Telecommunications", "Telecom", "Networks", "Telco")),
		Map.entry("telecom",            List.of("Telecom", "Telecommunications", "Networks", "Telco")),
		Map.entry("technology",         List.of("Technology", "Software", "IT", "SaaS", "Tech")),
		Map.entry("public sector",      List.of("Government", "Public Sector", "Defense", "NGO", "Public Administration")),
		Map.entry("government",         List.of("Government", "Public Sector", "Defense", "NGO")),
		Map.entry("real estate",        List.of("Real Estate", "PropTech", "Property")),
		Map.entry("consulting",         List.of("Consulting", "Professional Services", "Advisory")),
		Map.entry("professional services", List.of("Professional Services", "Consulting", "Advisory"))
	);

	// Degree-level regex patterns keyed by normalized value
	private static final Map<String, String> DEGREE_PATTERNS = Map.of(
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

		// When skills are specified, they are the hard AND constraint.
		// skillDimension already searches personalInformation.role and workExperience.position,
		// so non-English titles like "Développeur Java" match \bJava\b without a role fallback.
		// Roles are only used as the primary filter when no skills are specified (role-only queries).
		if (params.skills() != null && !params.skills().isEmpty()) {
			conditions.add(skillDimension(params.skills()));
		} else if (params.roles() != null && !params.roles().isEmpty()) {
			conditions.add(roleDimension(params.roles()));
		}

		if (params.industries() != null && !params.industries().isEmpty()) {
			List<Criteria> ic = params.industries().stream()
				.flatMap(ind -> expandIndustry(ind).stream())
				.distinct()
				.map(v -> Criteria.where("searchIndex.industries").regex(escape(v), "i"))
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

		// requiredSkills: each skill is a separate AND — candidate must have ALL of them
		if (params.requiredSkills() != null && !params.requiredSkills().isEmpty()) {
			params.requiredSkills().forEach(skill -> conditions.add(skillDimension(List.of(skill))));
		}

		// requiredIndustries: each sector group is AND'd; umbrella terms expand to OR within each group
		if (params.requiredIndustries() != null && !params.requiredIndustries().isEmpty()) {
			params.requiredIndustries().forEach(ind -> {
				List<Criteria> variantCriteria = expandIndustry(ind).stream()
					.map(v -> Criteria.where("searchIndex.industries").regex(escape(v), "i"))
					.collect(Collectors.toList());
				conditions.add(variantCriteria.size() == 1
					? variantCriteria.get(0)
					: new Criteria().orOperator(variantCriteria.toArray(new Criteria[0])));
			});
		}

		return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
	}

	/**
	 * Skill dimension: OR across English-normalized search fields.
	 * Uses word-boundary regex so "Node.js" doesn't bleed into "Vue.js".
	 * searchIndex.roles is included because a technology present in a role title implies the skill
	 * (e.g., "Node.js Developer" → candidate has Node.js).
	 */
	private Criteria skillDimension(List<String> skills) {
		List<Criteria> perTerm = skills.stream().map(skill -> {
			String pattern = "\\b" + escape(skill) + "\\b";
			return new Criteria().orOperator(
				Criteria.where("searchIndex.skills").regex(pattern, "i"),
				Criteria.where("searchIndex.roles").regex(pattern, "i")
			);
		}).collect(Collectors.toList());
		return new Criteria().orOperator(perTerm.toArray(new Criteria[0]));
	}

	/**
	 * Role dimension: matches against the English-normalized searchIndex.roles field.
	 * Uses substring regex (no word boundary) — role phrases are often substrings of longer titles.
	 *
	 * For multi-word role phrases, also extracts domain qualifier tokens (e.g. "backend" from
	 * "backend engineer") and searches for them with word-boundary regex. This handles stored
	 * values like "Backend Node.js Developer" that don't contain the full phrase as a substring.
	 */
	private Criteria roleDimension(List<String> roles) {
		List<Criteria> perTerm = roles.stream().map(role -> {
			String pattern = escape(role);
			List<Criteria> termFields = new ArrayList<>(List.of(
				Criteria.where("searchIndex.roles").regex(pattern, "i")
			));

			// For multi-word phrases, also match on domain qualifier tokens individually.
			List<String> words = Arrays.asList(role.toLowerCase().split("[\\s\\-]+"));
			if (words.size() > 1) {
				words.stream()
					.filter(w -> DOMAIN_QUALIFIERS.contains(w) && !ROLE_STOPWORDS.contains(w))
					.forEach(qualifier -> {
						String qp = "\\b" + escape(qualifier) + "\\b";
						termFields.add(Criteria.where("searchIndex.roles").regex(qp, "i"));
						termFields.add(Criteria.where("searchIndex.skills").regex(qp, "i"));
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

	/** Returns stored-level sector labels for an industry term, falling back to the term itself. */
	private List<String> expandIndustry(String industry) {
		return INDUSTRY_EXPANSION.getOrDefault(industry.toLowerCase().trim(), List.of(industry));
	}

	private static String degreePattern(String normalized) {
		String pattern = DEGREE_PATTERNS.get(normalized.toLowerCase());
		return pattern != null ? pattern : escape(normalized);
	}
}
