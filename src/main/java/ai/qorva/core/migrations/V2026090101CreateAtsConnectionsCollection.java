package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** A tenant's links to external ATS providers (credentials stored encrypted, see CredentialsCipher). */
@Slf4j
@Component
@ChangeUnit(id = "V20260901_01__CreateAtsConnectionsCollection", order = "20260901_01", author = "qorva")
public class V2026090101CreateAtsConnectionsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "ats_connections";
	private static final String DDL_FILE = "20260901_01__create_ats_connections_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20260901_01 – creating ats_connections collection", DDL_FILE);

		var connections = db.getCollection(COLLECTION);
		// One connection per provider per tenant — enforced in AtsConnectionService, backed here.
		connections.createIndex(new Document("tenantId", 1).append("provider", 1),
			new IndexOptions().unique(true).name("tenant_provider_idx"));
		connections.createIndex(new Document("tenantId", 1).append("createdAt", 1),
			new IndexOptions().name("tenant_created_idx"));
		// Scheduler sweep over CONNECTED connections.
		connections.createIndex(new Document("status", 1),
			new IndexOptions().name("status_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260901_01 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
