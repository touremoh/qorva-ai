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
 * Copilot runs. Indexes serve the three ways runs are read: a user's conversations and activity,
 * the worker's claim (status + lease), and the "one active run per user" check.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260930_02__CreateAgentRunsCollection", order = "20260930_02", author = "qorva")
public class V2026093002CreateAgentRunsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "agent_runs";
	private static final String DDL_FILE = "20260930_02__create_agent_runs_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20260930_02 – creating agent_runs collection", DDL_FILE);
		var runs = db.getCollection(COLLECTION);
		runs.createIndex(new Document("tenantId", 1).append("userEmail", 1).append("conversationId", 1).append("createdAt", 1),
			new IndexOptions().name("tenant_user_conversation_idx"));
		runs.createIndex(new Document("tenantId", 1).append("createdAt", -1),
			new IndexOptions().name("tenant_created_idx"));
		runs.createIndex(new Document("status", 1).append("leaseExpiresAt", 1).append("createdAt", 1),
			new IndexOptions().name("claim_idx"));
		runs.createIndex(new Document("tenantId", 1).append("userEmail", 1).append("status", 1),
			new IndexOptions().name("tenant_user_status_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260930_02 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
