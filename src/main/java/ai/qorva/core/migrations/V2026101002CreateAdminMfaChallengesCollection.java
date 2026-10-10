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

/** Admin sign-in codes: counted per admin for the rate limit, reaped by TTL once expired. */
@Slf4j
@Component
@ChangeUnit(id = "V20261010_02__CreateAdminMfaChallengesCollection", order = "20261010_02", author = "qorva")
public class V2026101002CreateAdminMfaChallengesCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "admin_mfa_challenges";

	@Execution
	public void execute(MongoDatabase db) {
		createCollectionIfAbsent(db, COLLECTION);
		var challenges = db.getCollection(COLLECTION);
		challenges.createIndex(new Document("adminId", 1).append("createdAt", -1), new IndexOptions().name("admin_mfa_admin_created_idx"));
		challenges.createIndex(new Document("expiresAt", 1), new IndexOptions().name("admin_mfa_ttl_idx").expireAfter(3600L, TimeUnit.SECONDS));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20261010_02 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
