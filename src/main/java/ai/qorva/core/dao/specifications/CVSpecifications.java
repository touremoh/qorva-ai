package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.CV;
import org.springframework.data.mongodb.core.query.Criteria;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CVSpecifications {
	private CVSpecifications() {
		throw new UnsupportedOperationException("Utility class");
	}

	/** Archived filter: the CV list shows active CVs by default; archived only on request. */
	public static MongoSpecification<CV> archivedEquals(boolean archived) {
		return archived
			? () -> Criteria.where("archived").is(true)
			: () -> Criteria.where("archived").ne(true);
	}

	public static MongoSpecification<CV> nameContains(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return MongoSpecifications.empty();
		}
		// User input is a literal, never a pattern: "C++" or "(" must not blow up the query.
		return () -> Criteria.where("personalInformation.name").regex(Pattern.quote(keyword.trim()), "i");
	}

	public static MongoSpecification<CV> hasRoleOrPosition(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("personalInformation.role").regex(Pattern.quote(keyword.trim()), "i");
	}

	public static MongoSpecification<CV> hasMinYearOfExperience(Integer minYears) {
		if (minYears == null || minYears < 0) {
			return MongoSpecifications.empty();
		}
		int latestAllowedStartYear = Year.now().getValue() - minYears;
		return () -> Criteria.where("careerStartYear").lte(latestAllowedStartYear);
	}

	/**
	 * Quick search box above the CV list: a literal, case-insensitive "contains" across the few
	 * fields a recruiter types from memory. Composes with the rail filters (AND), unlike the
	 * $text endpoint, and never stems or tokenises so the hit count is what it looks like.
	 */
	public static MongoSpecification<CV> quickSearch(String term) {
		if (term == null || term.isBlank()) {
			return MongoSpecifications.empty();
		}
		String pattern = Pattern.quote(term.trim());
		List<String> fields = List.of(
			"personalInformation.name",
			"personalInformation.role",
			"personalInformation.contact.email",
			"applicantNumber",
			"searchIndex.roles",
			"searchIndex.skills",
			"tags");
		return () -> new Criteria().orOperator(fields.stream()
			.map(f -> Criteria.where(f).regex(pattern, "i"))
			.toArray(Criteria[]::new));
	}

	public static MongoSpecification<CV> hasMaxYearOfExperience(Integer maxYears) {
		if (maxYears == null || maxYears < 0) {
			return MongoSpecifications.empty();
		}
		int earliestAllowedStartYear = Year.now().getValue() - maxYears;
		return () -> Criteria.where("careerStartYear").gte(earliestAllowedStartYear);
	}

	// -------------------------------------------------------------------------
	// CV list filters. Every spec below is an exact match on a value the user picked from
	// GET /cvs/filter-options, so the count shown next to the option equals the rows returned.
	// Normalized searchIndex fields are compared case-insensitively because extraction casing
	// drifts ("Fintech" / "FinTech") and the facet groups them by lower-case.
	// -------------------------------------------------------------------------

	public static MongoSpecification<CV> seniorityIn(Collection<String> values) {
		return fieldIn("candidateClustering.seniorityLevel", values);
	}

	public static MongoSpecification<CV> leadershipIn(Collection<String> values) {
		return fieldIn("candidateClustering.leadershipAndInfluence", values);
	}

	public static MongoSpecification<CV> availabilityStatusIn(Collection<String> values) {
		return fieldIn("personalInformation.availability.status", values);
	}

	public static MongoSpecification<CV> skillDepthIn(Collection<String> values) {
		return fieldIn("candidateClustering.skillDepth", values);
	}

	public static MongoSpecification<CV> tagsAnyOf(Collection<String> values) {
		return fieldIn("tags", values);
	}

	public static MongoSpecification<CV> industriesAnyOf(Collection<String> values) {
		return fieldMatchesAnyExact("searchIndex.industries", values);
	}

	public static MongoSpecification<CV> locationsAnyOf(Collection<String> values) {
		return fieldMatchesAnyExact("searchIndex.locations", values);
	}

	/** All-of: recruiters stacking skills mean "has every one of these", unlike the other list filters. */
	public static MongoSpecification<CV> skillsAllOf(Collection<String> values) {
		List<String> clean = clean(values);
		if (clean.isEmpty()) {
			return MongoSpecifications.empty();
		}
		return MongoSpecifications.allOf(clean.stream()
			.<MongoSpecification<CV>>map(v -> () -> Criteria.where("searchIndex.skills").regex(exact(v)))
			.toList());
	}

	/** {@code MANUAL} = no ATS reference; any other value is an ATS provider code (see AtsProviderEnum). */
	public static MongoSpecification<CV> sourceIn(Collection<String> values) {
		List<String> clean = clean(values);
		if (clean.isEmpty()) {
			return MongoSpecifications.empty();
		}
		boolean manual = clean.remove("MANUAL");
		List<MongoSpecification<CV>> alternatives = new ArrayList<>();
		if (manual) {
			alternatives.add(() -> new Criteria().orOperator(
				Criteria.where("atsRefs").exists(false),
				Criteria.where("atsRefs").size(0)));
		}
		if (!clean.isEmpty()) {
			alternatives.add(() -> Criteria.where("atsRefs.provider").in(clean));
		}
		return MongoSpecifications.anyOf(alternatives);
	}

	public static MongoSpecification<CV> createdAfter(Instant since) {
		return since == null ? MongoSpecifications.empty() : () -> Criteria.where("createdAt").gte(since);
	}

	public static MongoSpecification<CV> updatedAfter(Instant since) {
		return since == null ? MongoSpecifications.empty() : () -> Criteria.where("lastUpdatedAt").gte(since);
	}

	/** Sentinel the UI sends for the "Not analysed" bucket of an enum facet; matched as null/missing. */
	public static final String UNSET = "_unset";

	private static MongoSpecification<CV> fieldIn(String field, Collection<String> values) {
		List<String> clean = clean(values);
		if (clean.isEmpty()) {
			return MongoSpecifications.empty();
		}
		// $in: [null] matches both an explicit null and a missing field, which is what "not analysed" means.
		List<String> withNull = clean.stream().map(v -> UNSET.equals(v) ? null : v).toList();
		return () -> Criteria.where(field).in(withNull);
	}

	private static MongoSpecification<CV> fieldMatchesAnyExact(String field, Collection<String> values) {
		List<Pattern> patterns = clean(values).stream().map(CVSpecifications::exact).toList();
		return patterns.isEmpty() ? MongoSpecifications.empty() : () -> Criteria.where(field).in(patterns);
	}

	/** Anchored, quoted, case-insensitive: matches the whole array element and nothing else. */
	private static Pattern exact(String value) {
		return Pattern.compile("^" + Pattern.quote(value) + "$", Pattern.CASE_INSENSITIVE);
	}

	private static List<String> clean(Collection<String> values) {
		if (values == null) {
			return new ArrayList<>();
		}
		return values.stream()
			.filter(v -> v != null && !v.isBlank())
			.map(String::trim)
			.distinct()
			.collect(Collectors.toCollection(ArrayList::new));
	}

	public static MongoSpecification<CV> applicantNumberEquals(String applicantNumber) {
		if (applicantNumber == null || applicantNumber.isBlank()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("applicantNumber").is(applicantNumber);
	}

	public static MongoSpecification<CV> applicantNumberIn(List<String> applicantNumbers) {
		if (applicantNumbers == null || applicantNumbers.isEmpty()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("applicantNumber").in(applicantNumbers);
	}
}
