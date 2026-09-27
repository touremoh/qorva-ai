package ai.qorva.core.migrations;

import ai.qorva.core.it.AbstractIntegrationTest;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The VIEW_LIBRARY_INSIGHTS grant restores the action where an app save dropped it, and only there. */
class ViewLibraryInsightsGrantIntegrationTest extends AbstractIntegrationTest {

	private static final String COLLECTION = "migration_grant_probe_users";

	@Autowired private MongoTemplate mongoTemplate;

	// Dropped before and after: the purge contract tests count every collection in the shared database.
	@BeforeEach
	@AfterEach
	void clean() {
		mongoTemplate.dropCollection(COLLECTION);
	}

	@Test
	void grantsToViewCvHoldersWhoLostIt_underTheirViewCvRole_andIsIdempotent() {
		var db = mongoTemplate.getDb();
		var users = db.getCollection(COLLECTION);
		var lost = new ObjectId();
		var kept = new ObjectId();
		var noViewCv = new ObjectId();
		users.insertMany(List.of(
			user(lost, authority("ACCOUNT_MANAGER", "VIEW_CV")),
			user(kept, authority("ACCOUNT_OWNER", "VIEW_CV"), authority("ACCOUNT_OWNER", "VIEW_LIBRARY_INSIGHTS")),
			user(noViewCv, authority("ACCOUNT_MANAGER", "VIEW_DASHBOARD"))));

		assertThat(V2026092601BackfillViewLibraryInsightsAuthority.grantToViewCvHolders(db, COLLECTION)).isEqualTo(1);
		assertThat(V2026092601BackfillViewLibraryInsightsAuthority.grantToViewCvHolders(db, COLLECTION)).isZero();

		assertThat(actions(users.find(new Document("_id", lost)).first()))
			.contains("VIEW_LIBRARY_INSIGHTS");
		assertThat(roleOf(users.find(new Document("_id", lost)).first(), "VIEW_LIBRARY_INSIGHTS"))
			.isEqualTo("ACCOUNT_MANAGER");
		assertThat(actions(users.find(new Document("_id", kept)).first()))
			.filteredOn("VIEW_LIBRARY_INSIGHTS"::equals).hasSize(1);
		assertThat(actions(users.find(new Document("_id", noViewCv)).first()))
			.doesNotContain("VIEW_LIBRARY_INSIGHTS");
	}

	private static Document user(ObjectId id, Document... authorities) {
		return new Document("_id", id).append("authorities", List.of(authorities));
	}

	private static Document authority(String role, String action) {
		return new Document("role", role).append("action", action).append("permission", "ALLOWED");
	}

	private static List<String> actions(Document user) {
		return user.getList("authorities", Document.class).stream().map(a -> a.getString("action")).toList();
	}

	private static String roleOf(Document user, String action) {
		return user.getList("authorities", Document.class).stream()
			.filter(a -> action.equals(a.getString("action"))).map(a -> a.getString("role")).findFirst().orElse(null);
	}
}
