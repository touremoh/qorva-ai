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
 * Support requests sent from Qorva Help. Indexes serve the per-user daily limit, the email retry sweep
 * and lookups by the reference the user quotes.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261006_02__CreateSupportTicketsCollection", order = "20261006_02", author = "qorva")
public class V2026100602CreateSupportTicketsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "support_tickets";
	private static final String DDL_FILE = "20261006_02__create_support_tickets_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20261006_02 – creating support_tickets collection", DDL_FILE);
		var tickets = db.getCollection(COLLECTION);
		tickets.createIndex(new Document("tenantId", 1).append("userEmail", 1).append("createdAt", -1),
			new IndexOptions().name("tenant_user_created_idx"));
		tickets.createIndex(new Document("emailStatus", 1).append("createdAt", 1),
			new IndexOptions().name("email_status_idx"));
		tickets.createIndex(new Document("reference", 1),
			new IndexOptions().name("reference_unique_idx").unique(true));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20261006_02 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
