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
 * Qorva Help conversations: read by owner (tenant + user + id), deleted by Mongo at {@code expiresAt}
 * (90 days after the last message by default) — users may type personal data into them.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261006_01__CreateHelpConversationsCollection", order = "20261006_01", author = "qorva")
public class V2026100601CreateHelpConversationsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "help_conversations";
	private static final String DDL_FILE = "20261006_01__create_help_conversations_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20261006_01 – creating help_conversations collection", DDL_FILE);
		var conversations = db.getCollection(COLLECTION);
		conversations.createIndex(new Document("tenantId", 1).append("userEmail", 1).append("updatedAt", -1),
			new IndexOptions().name("tenant_user_updated_idx"));
		conversations.createIndex(new Document("expiresAt", 1),
			new IndexOptions().name("expires_ttl_idx").expireAfter(0L, TimeUnit.SECONDS));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20261006_01 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
