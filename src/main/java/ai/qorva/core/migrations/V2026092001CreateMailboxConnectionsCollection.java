package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * Recruiters' connected mailboxes (delegated OAuth tokens, encrypted) used to send candidate
 * outreach as themselves. One connection per user, hence the unique index.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260920_01__CreateMailboxConnectionsCollection", order = "20260920_01", author = "qorva")
public class V2026092001CreateMailboxConnectionsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "mailbox_connections";
	private static final String DDL_FILE = "20260920_01__create_mailbox_connections_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20260920_01 – creating mailbox_connections collection", DDL_FILE);
		db.getCollection(COLLECTION).createIndex(
			new Document("tenantId", 1).append("userId", 1),
			new IndexOptions().name("tenant_user_unique_idx").unique(true));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260920_01 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
