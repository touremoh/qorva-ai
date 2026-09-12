package ai.qorva.core.dao.querybuilder;

import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CVQueryBuilderTest {

	private final CVQueryBuilder builder = new CVQueryBuilder();

	private static Map<String, String> params(String... kv) {
		Map<String, String> m = new HashMap<>();
		m.put("tenantId", "66f0c2a1b2c3d4e5f6a7b8c9");
		for (int i = 0; i < kv.length; i += 2) {
			m.put(kv[i], kv[i + 1]);
		}
		return m;
	}

	private String json(Map<String, String> params) {
		return builder.buildQuery(params).toCriteria().getCriteriaObject().toJson();
	}

	@Test
	void enumFiltersSplitOnCommaAndDropBlanks() {
		String q = json(params("seniority", " senior, lead,,  "));

		assertThat(q).contains("candidateClustering.seniorityLevel");
		assertThat(q).contains("\"senior\"").contains("\"lead\"");
		assertThat(q).doesNotContain("\"\"");
	}

	@Test
	void industriesAreOrWithinTheFilterAndAnchoredCaseInsensitive() {
		String q = json(params("industries", "Banking,Insurance"));

		// one $in with both patterns → any-of
		assertThat(q).contains("searchIndex.industries");
		assertThat(q).contains("^\\\\QBanking\\\\E$").contains("^\\\\QInsurance\\\\E$");
		assertThat(q).contains("\"options\": \"i\"");
	}

	@Test
	void skillsAreAndAcrossValues() {
		String q = json(params("skills", "Java,Kubernetes"));

		// all-of → two separate regex conditions, not one $in
		assertThat(q).containsPattern("searchIndex\\.skills.*searchIndex\\.skills");
		assertThat(q).doesNotContain("$in");
	}

	@Test
	void regexMetacharactersInUserInputAreLiterals() {
		assertThatCode(() -> json(params("skills", "C++,C#,(x)", "name", "O'Brien (", "industries", "R&D [x]")))
			.doesNotThrowAnyException();

		String q = json(params("name", "C++ ("));
		assertThat(q).contains("\\\\QC++ (\\\\E");
	}

	@Test
	void experienceRangeMapsToCareerStartYearBounds() {
		int now = Year.now().getValue();
		String q = json(params("minYearsOfExperience", "5", "maxYearsOfExperience", "10"));

		assertThat(q).contains("\"$lte\": " + (now - 5));
		assertThat(q).contains("\"$gte\": " + (now - 10));
	}

	@Test
	void nonNumericExperienceIsIgnored() {
		String q = json(params("minYearsOfExperience", "abc"));

		assertThat(q).doesNotContain("careerStartYear");
	}

	@Test
	void sourceManualMeansNoAtsRefsAndProvidersAreExact() {
		String manual = json(params("source", "MANUAL"));
		assertThat(manual).contains("\"atsRefs\"").contains("$exists").contains("$size");

		String provider = json(params("source", "greenhouse,lever"));
		assertThat(provider).contains("atsRefs.provider").contains("\"greenhouse\"").contains("\"lever\"");
	}

	@Test
	void dateFiltersAcceptPlainDatesAndIgnoreGarbage() {
		// Instant has no plain-bson codec (Spring's converter handles it at runtime), so inspect the Document itself
		String q = builder.buildQuery(params("createdAfter", "2026-09-01", "updatedAfter", "not-a-date"))
			.toCriteria().getCriteriaObject().toString();

		assertThat(q).contains("createdAt").contains("2026-09-01T00:00:00Z");
		assertThat(q).doesNotContain("lastUpdatedAt");
	}

	@Test
	void archivedDefaultsToActiveOnly() {
		assertThat(json(params())).contains("\"archived\": {\"$ne\": true}");
		assertThat(json(params("archived", "true"))).contains("\"archived\": true");
	}
}
