package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Asynchronous bulk-operation queue (re-analyze, candidate-update campaigns). */
@Slf4j
@Component
@ChangeUnit(id = "V20260727_03__CreateBackgroundJobsCollection", order = "20260727_03", author = "qorva")
public class V2026072703CreateBackgroundJobsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "background_jobs";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260727_03 – creating {} collection", COLLECTION);
		db.createCollection(COLLECTION);
		var jobs = db.getCollection(COLLECTION);
		// Worker claim path: PENDING jobs or RUNNING jobs whose lease expired.
		jobs.createIndex(new Document("status", 1).append("leaseExpiresAt", 1),
			new IndexOptions().name("status_lease_idx"));
		jobs.createIndex(new Document("tenantId", 1).append("createdAt", -1),
			new IndexOptions().name("tenant_created_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260727_03 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
