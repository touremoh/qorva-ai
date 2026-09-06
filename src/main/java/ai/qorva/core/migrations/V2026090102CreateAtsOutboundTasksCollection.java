package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Queued score write-backs to an ATS, drained with retry/backoff by AtsWriteBackService. */
@Slf4j
@Component
@ChangeUnit(id = "V20260901_02__CreateAtsOutboundTasksCollection", order = "20260901_02", author = "qorva")
public class V2026090102CreateAtsOutboundTasksCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "ats_outbound_tasks";
	private static final String DDL_FILE = "20260901_02__create_ats_outbound_tasks_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20260901_02 – creating ats_outbound_tasks collection", DDL_FILE);

		var tasks = db.getCollection(COLLECTION);
		// Drain path: PENDING tasks whose backoff has elapsed, oldest first.
		tasks.createIndex(new Document("status", 1).append("nextAttemptAt", 1),
			new IndexOptions().name("status_next_attempt_idx"));
		// Cascade delete when a connection is removed.
		tasks.createIndex(new Document("connectionId", 1),
			new IndexOptions().name("connection_idx"));
		tasks.createIndex(new Document("tenantId", 1).append("createdAt", -1),
			new IndexOptions().name("tenant_created_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260901_02 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
