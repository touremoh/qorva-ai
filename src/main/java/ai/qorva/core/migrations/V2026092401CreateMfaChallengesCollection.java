package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Pending email MFA codes (hashed). Rows are short-lived: the TTL index reaps them once
 * {@code expiresAt} has passed; the per-user index backs the challenge-creation rate limit.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260924_01__CreateMfaChallengesCollection", order = "20260924_01", author = "qorva")
public class V2026092401CreateMfaChallengesCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "mfa_challenges";
	private static final String DDL_FILE = "20260924_01__create_mfa_challenges_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20260924_01 – creating mfa_challenges collection", DDL_FILE);
		var collection = db.getCollection(COLLECTION);
		collection.createIndex(
			new Document("expiresAt", 1),
			new IndexOptions().name("mfa_expiry_ttl_idx").expireAfter(0L, TimeUnit.SECONDS));
		collection.createIndex(
			new Document("userId", 1).append("createdAt", -1),
			new IndexOptions().name("mfa_user_created_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260924_01 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
