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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * Location filter  → searchIndex.locations (city, region, country, continent — in English)
 *
 * Raw CV fields stay in the CV's own language, so text filters never read them; the two
 * exceptions (degree, language) use patterns that cover the main European spellings.
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
		Map.entry("healthcare",         List.of("Healthcare", "Health", "Medical", "Pharma", "Pharmaceutical", "Bioinformatics", "Life Sciences", "Hospital", "Clinic", "Biotech", "CRO")),
		Map.entry("health",             List.of("Healthcare", "Health", "Medical", "Pharma", "Pharmaceutical", "Hospital", "Clinic")),
		Map.entry("pharma",             List.of("Pharma", "Pharmaceutical", "Biotech", "Life Sciences", "CRO")),
		Map.entry("life sciences",      List.of("Life Sciences", "Biotech", "Pharma", "Pharmaceutical", "Bioinformatics", "CRO")),
		Map.entry("retail",             List.of("Retail", "E-Commerce", "eCommerce", "Consumer Goods")),
		Map.entry("e-commerce",         List.of("E-Commerce", "eCommerce", "Retail", "Consumer Goods")),
		Map.entry("energy",             List.of("Energy", "Oil & Gas", "Oil and Gas", "Renewable", "Utilities", "Clean Energy")),
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
	// Degrees are the one raw field a text filter reads, so each pattern lists the abbreviations
	// and the main European spellings a CV carries (BEng, BComm, Licence, Laurea, Diplom…).
	// Abbreviations are word-bounded so "BA" does not fire inside "MBA" or "Bachelor".
	private static final Map<String, String> DEGREE_PATTERNS = Map.of(
		"bachelor",  "bachelor|bachelier|bachiller|bacharel|\\bB\\.?\\s?(Sc|S|A|Eng|E|Comm|Com|BA|Tech|Ed|Fin|N)\\b|\\bMBBS\\b|\\bMBChB\\b|\\bLLB\\b|undergraduate|licenc|licens|diplomatura|laurea triennale|\\bgrado\\b|ingenier.a t.cnica",
		"master",    "m.st[eè]re?\\b|\\bM\\.?\\s?(Sc|S|A|Eng|E|Phil|Res|Ed|Fin|Tech|St)\\b|(?<!under)graduate|magist|maestr|mestrado|laurea(?! triennale)|\\bDiplom\\b|diplomingenieur|dipl\\.?-?ing|dipl.me d.ing|staatsexamen|^ingenier.a(?! t.cnica)",
		"phd",       "ph\\.?d\\.?|doctor|docteur|doktor|dottorato|doutoramento|\\bDPhil\\b|D\\.Phil|\\bDr\\.?\\s?(rer|phil|ing|med|sc)|promotion",
		"mba",       "\\bE?MBA\\b|master of business",
		"associate", "associate|\\bHND\\b|\\bHNC\\b|\\bBTS\\b|\\bDUT\\b|\\bBUT\\b|\\bAAS\\b|foundation degree|higher certificate"
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

		List<String> industries = withoutSkillDuplicates(params.industries(), params);
		if (!industries.isEmpty()) {
			List<Criteria> ic = industries.stream()
				.flatMap(ind -> expandIndustry(ind).stream())
				.distinct()
				.map(v -> Criteria.where("searchIndex.industries").regex(prefixBounded(v), "i"))
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
		if (params.location() != null && !params.location().isBlank()) {
			// contact.address is a nested document in the CV's language; a regex on it can never
			// match. searchIndex.locations lists city, region, country and continent in English.
			conditions.add(Criteria.where("searchIndex.locations").regex(prefixBounded(params.location()), "i"));
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
					.map(v -> Criteria.where("searchIndex.industries").regex(prefixBounded(v), "i"))
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

	/**
	 * Drops industry terms that the extractor also emitted as a skill. That duplication is the
	 * extractor hedging on a term that is not a sector at all ("economics" came back as both
	 * {@code skills: ["economics"]} and {@code industries: ["Economics"]}), and because industries
	 * are AND'd onto the skill dimension the hedge turned into a filter no candidate could pass:
	 * a field of study is indexed under {@code searchIndex.skills}, never under
	 * {@code searchIndex.industries}. Terms that only appear under industries are kept as-is.
	 */
	private static List<String> withoutSkillDuplicates(List<String> industries, CVQueryParams params) {
		if (industries == null || industries.isEmpty()) {
			return List.of();
		}
		Set<String> skillTerms = Stream.concat(
				params.skills() != null ? params.skills().stream() : Stream.empty(),
				params.requiredSkills() != null ? params.requiredSkills().stream() : Stream.empty())
			.map(s -> s.toLowerCase(Locale.ROOT).trim())
			.collect(Collectors.toSet());
		if (skillTerms.isEmpty()) {
			return industries;
		}
		List<String> kept = industries.stream()
			.filter(ind -> !skillTerms.contains(ind.toLowerCase(Locale.ROOT).trim()))
			.collect(Collectors.toList());
		if (kept.size() != industries.size()) {
			log.debug("Ignoring industry terms already searched as skills: {}", industries.stream().filter(i -> !kept.contains(i)).toList());
		}
		return kept;
	}

	/**
	 * A term that must start a word in the stored label. Plain substring matching let "IT"
	 * fire inside "Hospitality", "Utilities" and "Recruitment"; a leading boundary stops that
	 * while still letting "Pharma" reach "Pharmaceuticals" and "Hospital" reach "Hospitals".
	 * Short tokens are acronyms, and a prefix match on those ("IT" in "Italy", "CRO" in
	 * "Croatia") is noise, so they are bounded on both sides.
	 */
	static String prefixBounded(String term) {
		String trimmed = term.trim();
		String pattern = "\\b" + escape(trimmed);
		return trimmed.length() <= 3 ? pattern + "\\b" : pattern;
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
