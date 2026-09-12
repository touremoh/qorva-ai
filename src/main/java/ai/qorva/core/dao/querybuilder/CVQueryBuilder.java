package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.specifications.CVSpecifications;
import ai.qorva.core.dao.specifications.MongoSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Turns the query string of {@code GET /cvs} into a specification.
 *
 * Every filter here is an exact match on a value the UI took from {@code GET /cvs/filter-options}
 * (see {@link CVSpecifications}); fuzzy matching lives in {@link InsightCVQueryBuilder} for
 * Talent Intelligence. Multi-value params are comma-separated because the controller hands
 * us a {@code Map<String,String>}, which keeps only one value per key.
 */
@Slf4j
@Component
public class CVQueryBuilder implements QorvaQueryBuilder<CV> {

	@Override
	public MongoSpecification<CV> buildQuery(Map<String, String> params) {
		log.debug("CVQueryBuilder.buildQuery params: {}", params);

		return MongoSpecification
			.where(CVSpecifications.tenantIdEquals(params.get("tenantId")))
			.and(CVSpecifications.archivedEquals("true".equals(params.get("archived"))))
			.and(CVSpecifications.applicantNumberEquals(params.get("applicantNumber")))
			.and(CVSpecifications.nameContains(params.get("name")))
			.and(CVSpecifications.hasRoleOrPosition(params.get("role")))
			.and(CVSpecifications.seniorityIn(list(params.get("seniority"))))
			.and(CVSpecifications.leadershipIn(list(params.get("leadership"))))
			.and(CVSpecifications.availabilityStatusIn(list(params.get("availability"))))
			.and(CVSpecifications.skillDepthIn(list(params.get("skillDepth"))))
			.and(CVSpecifications.industriesAnyOf(list(params.get("industries"))))
			.and(CVSpecifications.locationsAnyOf(list(params.get("locations"))))
			.and(CVSpecifications.skillsAllOf(list(params.get("skills"))))
			.and(CVSpecifications.tagsAnyOf(list(params.get("tags"))))
			.and(CVSpecifications.sourceIn(list(params.get("source"))))
			.and(CVSpecifications.hasMinYearOfExperience(integer(params.get("minYearsOfExperience"))))
			.and(CVSpecifications.hasMaxYearOfExperience(integer(params.get("maxYearsOfExperience"))))
			.and(CVSpecifications.createdAfter(instant(params.get("createdAfter"))))
			.and(CVSpecifications.updatedAfter(instant(params.get("updatedAfter"))));
	}

	static List<String> list(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}
		return Arrays.stream(csv.split(","))
			.map(String::trim)
			.filter(v -> !v.isEmpty())
			.toList();
	}

	static Integer integer(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			log.debug("Ignoring non-numeric filter value '{}'", value);
			return null;
		}
	}

	/** Accepts an ISO instant ("2026-09-01T00:00:00Z") or a plain date ("2026-09-01", read as UTC midnight). */
	static Instant instant(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String v = value.trim();
		try {
			return v.length() == 10 ? Instant.parse(v + "T00:00:00Z") : Instant.parse(v);
		} catch (DateTimeParseException e) {
			log.debug("Ignoring unparsable date filter value '{}'", value);
			return null;
		}
	}
}
