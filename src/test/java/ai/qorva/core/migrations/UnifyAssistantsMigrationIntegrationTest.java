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

/** Demo users get Copilot, and nobody keeps a chat or library-insights action. */
class UnifyAssistantsMigrationIntegrationTest extends AbstractIntegrationTest {

	private static final String COLLECTION = "migration_unify_probe_users";

	@Autowired private MongoTemplate mongoTemplate;

	// Dropped before and after: the purge contract tests count every collection in the shared database.
	@BeforeEach
	@AfterEach
	void clean() {
		mongoTemplate.dropCollection(COLLECTION);
	}

	@Test
	void grantsCopilotToDemoUsersAndRemovesTheOldActions_idempotently() {
		var db = mongoTemplate.getDb();
		var users = db.getCollection(COLLECTION);
		var demo = new ObjectId();
		var owner = new ObjectId();
		users.insertMany(List.of(
			user(demo, "VIEW_CV", "VIEW_JOB", "GENERATE_REPORT", "VIEW_REPORT", "VIEW_LIBRARY_INSIGHTS"),
			user(owner, "ADD_CV", "VIEW_CV", "START_CHAT", "VIEW_CHAT", "VIEW_MESSAGE", "REPLY_MESSAGE", "MODIFY_CHAT",
				"DELETE_CHAT", "VIEW_LIBRARY_INSIGHTS", "USE_AGENT")));

		V2026100506UnifyAssistantsIntoCopilot.moveToCopilot(db, COLLECTION);
		V2026100506UnifyAssistantsIntoCopilot.moveToCopilot(db, COLLECTION);

		assertThat(actions(users.find(new Document("_id", demo)).first()))
			.containsExactly("VIEW_CV", "VIEW_JOB", "GENERATE_REPORT", "VIEW_REPORT", "USE_AGENT");
		assertThat(actions(users.find(new Document("_id", owner)).first()))
			.containsExactly("ADD_CV", "VIEW_CV", "USE_AGENT");
	}

	private static Document user(ObjectId id, String... actions) {
		return new Document("_id", id).append("authorities", List.of(actions).stream()
			.map(a -> new Document("role", "ACCOUNT_OWNER").append("action", a).append("permission", "ALLOWED")).toList());
	}

	private static List<String> actions(Document user) {
		return user.getList("authorities", Document.class).stream().map(a -> a.getString("action")).toList();
	}
}
