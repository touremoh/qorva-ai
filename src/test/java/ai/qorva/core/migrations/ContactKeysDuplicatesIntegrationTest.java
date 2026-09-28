package ai.qorva.core.migrations;

import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.it.AbstractIntegrationTest;
import ai.qorva.core.utils.ContactNormalizer;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same person is one duplicate group whatever the email's letter case or the phone's
 * formatting — for resumes stored before the change (backfill) and at upload time.
 */
class ContactKeysDuplicatesIntegrationTest extends AbstractIntegrationTest {

	@Autowired private MongoTemplate mongoTemplate;
	@Autowired private CVRepository cvRepository;

	private final ObjectId tenant = new ObjectId();

	// Only this test's tenant is touched; the purge contract tests count documents in the shared database.
	@BeforeEach
	@AfterEach
	void clean() {
		mongoTemplate.getDb().getCollection("cvs").deleteMany(new Document("tenantId", tenant));
	}

	@Test
	void legacyResumes_areBackfilled_andGroupedByNormalisedContact() {
		var cvs = mongoTemplate.getDb().getCollection("cvs");
		var first = new ObjectId();
		var second = new ObjectId();
		var other = new ObjectId();
		cvs.insertMany(List.of(
			cv(first, "Jane Doe", "Jane.Doe@Example.com", "+32 470 12 34 56", null),
			cv(second, "Jane Doe", " jane.doe@example.com", "0470/12.34.56", "Belgique"),
			cv(other, "John Roe", "john@roe.io", "+44 20 7946 0958", null)));

		assertThat(V2026092801NormaliseContactKeys.backfill(cvs)).isGreaterThanOrEqualTo(3);
		assertThat(V2026092801NormaliseContactKeys.backfill(cvs)).as("re-run changes nothing").isZero();
		assertThat(cvs.find(new Document("_id", second)).first().getEmbedded(List.of("contactKeys", "phone"), String.class))
			.isEqualTo("+32470123456");

		var stats = cvRepository.duplicateStats(tenant);
		assertThat(stats.groupCount()).as("one email group and one phone group").isEqualTo(2);
		assertThat(stats.excessCount()).isEqualTo(2);

		var groups = cvRepository.findDuplicateGroups(tenant, 0, 10).content();
		assertThat(groups).extracting(CVDuplicatesData.DuplicateGroup::matchType).containsExactlyInAnyOrder("EMAIL", "PHONE");
		var byType = groups.stream().collect(Collectors.toMap(
			CVDuplicatesData.DuplicateGroup::matchType, CVDuplicatesData.DuplicateGroup::matchValue));
		// Recruiters see one of the resumes' own values, never the normalised key.
		assertThat(byType.get("EMAIL")).isIn("Jane.Doe@Example.com", " jane.doe@example.com");
		assertThat(byType.get("PHONE")).isIn("+32 470 12 34 56", "0470/12.34.56");
	}

	@Test
	void uploadCheck_findsTheSamePersonFormattedDifferently() {
		var cvs = mongoTemplate.getDb().getCollection("cvs");
		var existing = new ObjectId();
		cvs.insertOne(cv(existing, "Jane Doe", "Jane.Doe@Example.com", "+32 470 12 34 56", null));
		V2026092801NormaliseContactKeys.backfill(cvs);

		var incoming = ContactNormalizer.keysOf("someone@else.io", "0032 470 123 456", null);
		var match = cvRepository.findContactMatch(tenant, incoming, new ObjectId());

		assertThat(match).isPresent();
		assertThat(match.get().getId()).isEqualTo(existing.toHexString());
		assertThat(cvRepository.findContactMatch(tenant, ContactNormalizer.keysOf("nobody@x.io", "+33 6 12 34 56 78", null),
			new ObjectId())).isEmpty();
	}

	private Document cv(ObjectId id, String name, String email, String phone, String country) {
		var contact = new Document("email", email).append("phone", phone);
		if (country != null) contact.append("address", new Document("country", country));
		var now = new Date();
		return new Document("_id", id)
			.append("tenantId", tenant)
			.append("applicantNumber", UUID.randomUUID().toString())
			.append("createdAt", now)
			.append("lastUpdatedAt", now)
			.append("createdBy", "test@qorva.test")
			.append("lastUpdatedBy", "test@qorva.test")
			.append("personalInformation", new Document("name", name).append("contact", contact));
	}
}
