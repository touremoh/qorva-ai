package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dto.CVQueryParams;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class InsightCVQueryBuilderTest {

	private final InsightCVQueryBuilder builder = new InsightCVQueryBuilder();
	private final ObjectId tenantId = new ObjectId();

	private static CVQueryParams params(List<String> skills, List<String> industries) {
		return new CVQueryParams(
			skills, List.of(), industries, List.of(), List.of(), List.of(), List.of(),
			null, null, null, null, null, null, null, List.of(), null,
			List.of(), List.of(), null, List.of(), null
		);
	}

	/**
	 * The regression this guards: "show me top profiles in the field of economics" was extracted as
	 * skills [economics, ...] AND industries [Economics]. Economics is a field of study, indexed as a
	 * skill and never as a sector, so the industry clause matched nobody and the count came back 0.
	 */
	@Test
	void anIndustryTermThatIsAlsoASkillDoesNotBecomeAHardFilter() {
		var criteria = builder.build(tenantId,
			params(List.of("economics", "economic analysis"), List.of("Economics")));

		String query = criteria.getCriteriaObject().toJson();

		assertThat(query).contains("searchIndex.skills");
		assertThat(query).doesNotContain("searchIndex.industries");
	}

	@Test
	void aGenuineIndustryIsStillFilteredAlongsideSkills() {
		var criteria = builder.build(tenantId, params(List.of("Java"), List.of("Banking")));

		String query = criteria.getCriteriaObject().toJson();

		assertThat(query).contains("searchIndex.skills");
		assertThat(query).contains("searchIndex.industries");
		assertThat(query).contains("Banking");
	}

	@Test
	void onlyTheDuplicatedTermIsDropped() {
		var criteria = builder.build(tenantId,
			params(List.of("economics"), List.of("Economics", "Banking")));

		Document query = criteria.getCriteriaObject();
		String json = query.toJson();

		assertThat(json).contains("Banking");
		assertThat(json).doesNotContain("\"pattern\": \"Economics\"");
	}

	private static CVQueryParams withLocation(String location) {
		return new CVQueryParams(
			List.of("Java"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
			null, null, null, null, null, location, null, List.of(), null,
			List.of(), List.of(), null, List.of(), null
		);
	}

	private static CVQueryParams withDegree(String level) {
		return new CVQueryParams(
			List.of(), List.of(), List.of(), List.of(), List.of(), List.of(level), List.of(),
			null, null, null, null, null, null, null, List.of(), null,
			List.of(), List.of(), null, List.of(), null
		);
	}

	/**
	 * contact.address is a nested document in the CV's language; a regex on it never matched.
	 * The English search index is the only place a location filter can read.
	 */
	@Test
	void locationReadsTheEnglishSearchIndexNotTheRawContact() {
		String query = builder.build(tenantId, withLocation("Belgium")).getCriteriaObject().toJson();

		assertThat(query).contains("searchIndex.locations");
		assertThat(query).doesNotContain("personalInformation.contact");
	}

	@Test
	void aBlankLocationAddsNoFilter() {
		String query = builder.build(tenantId, withLocation("  ")).getCriteriaObject().toJson();

		assertThat(query).doesNotContain("searchIndex.locations");
	}

	/** "IT" used to match Hospitality, Utilities and Recruitment by plain substring. */
	@Test
	void industryTermsMustStartAWordInTheStoredLabel() {
		Pattern it = Pattern.compile(InsightCVQueryBuilder.prefixBounded("IT"), Pattern.CASE_INSENSITIVE);
		assertThat(it.matcher("IT Services").find()).isTrue();
		assertThat(it.matcher("Hospitality").find()).isFalse();
		assertThat(it.matcher("Utilities").find()).isFalse();
		assertThat(it.matcher("Italy").find()).isFalse();

		Pattern pharma = Pattern.compile(InsightCVQueryBuilder.prefixBounded("Pharma"), Pattern.CASE_INSENSITIVE);
		assertThat(pharma.matcher("Pharmaceuticals").find()).isTrue();
		assertThat(pharma.matcher("Biopharma").find()).isFalse();
	}

	@Test
	void healthcareExpandsToTheLabelsHospitalsAndBiotechCvsCarry() {
		String query = builder.build(tenantId, params(List.of(), List.of("healthcare"))).getCriteriaObject().toJson();

		assertThat(query).contains("Hospital").contains("Biotech").contains("Clinic");
	}

	/** Every abbreviation a CV actually carries, none of them bleeding into another level. */
	@Test
	void degreePatternsCoverStoredAbbreviations() {
		Pattern bachelor = degreePattern("bachelor");
		Pattern master = degreePattern("master");
		Pattern phd = degreePattern("phd");
		Pattern mba = degreePattern("mba");

		for (String d : List.of("BA", "BS", "BEng", "BComm", "BBA", "BA (Hons)", "BSc Economics", "MBBS", "Licenciatura em Economia", "Grado en Ingeniería Civil", "Laurea Triennale")) {
			assertThat(bachelor.matcher(d).find()).as("bachelor: %s", d).isTrue();
			assertThat(master.matcher(d).find()).as("not master: %s", d).isFalse();
		}
		for (String d : List.of("MA", "MS", "MEng", "MSc", "Master", "Máster en Finanzas", "Mastère Spécialisé", "Mestrado em Economia", "Diplom-Ingenieur", "Laurea Magistrale", "Diplôme d'ingénieur", "Ingeniería Industrial")) {
			assertThat(master.matcher(d).find()).as("master: %s", d).isTrue();
			assertThat(bachelor.matcher(d).find()).as("not bachelor: %s", d).isFalse();
		}
		for (String d : List.of("PhD", "Ph.D.", "Doctorat en Médecine", "Dottorato di Ricerca", "Promotion (Dr. rer. nat.)", "Doktorat")) {
			assertThat(phd.matcher(d).find()).as("phd: %s", d).isTrue();
		}
		assertThat(mba.matcher("Executive MBA").find()).isTrue();
		assertThat(mba.matcher("MBBS").find()).isFalse();
		assertThat(bachelor.matcher("MBA").find()).as("BA inside MBA").isFalse();
		assertThat(master.matcher("undergraduate").find()).as("graduate inside undergraduate").isFalse();
		assertThat(master.matcher("Level 3 Diploma").find()).as("Diploma is not Diplom").isFalse();
	}

	private Pattern degreePattern(String level) {
		Document query = builder.build(tenantId, withDegree(level)).getCriteriaObject();
		// { $and: [ {tenantId}, { $or: [ { education.degree: /.../i } ] } ] }
		List<?> and = query.getList("$and", Object.class);
		Document or = ((Document) and.get(1)).getList("$or", Document.class).get(0);
		Object regex = or.get("education.degree");
		String pattern = regex instanceof org.bson.BsonRegularExpression bson
			? bson.getPattern()
			: ((java.util.regex.Pattern) regex).pattern();
		return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
	}

	@Test
	void industriesAloneAreUntouched() {
		var criteria = builder.build(tenantId, params(List.of(), List.of("Economics")));

		assertThat(criteria.getCriteriaObject().toJson()).contains("searchIndex.industries");
	}
}
