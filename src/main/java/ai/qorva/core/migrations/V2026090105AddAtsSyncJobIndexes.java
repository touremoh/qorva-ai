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
 * ATS_SYNC background jobs are looked up by connection — the scheduler skips a connection
 * that already has a sync in flight, and the UI lists a connection's recent syncs.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260901_05__AddAtsSyncJobIndexes", order = "20260901_05", author = "qorva")
public class V2026090105AddAtsSyncJobIndexes extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "background_jobs";
	private static final String CONNECTION_STATUS_IDX = "connection_status_idx";
	private static final String TENANT_CONNECTION_IDX = "tenant_connection_created_idx";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260901_05 – adding ATS sync job indexes on {}", COLLECTION);
		var jobs = db.getCollection(COLLECTION);
		// Only ATS sync jobs carry a connectionId; keep both indexes off the rest of the queue.
		var onlyAtsJobs = new Document("connectionId", new Document("$exists", true));
		jobs.createIndex(new Document("connectionId", 1).append("status", 1),
			new IndexOptions().name(CONNECTION_STATUS_IDX).partialFilterExpression(onlyAtsJobs));
		jobs.createIndex(new Document("tenantId", 1).append("connectionId", 1).append("createdAt", -1),
			new IndexOptions().name(TENANT_CONNECTION_IDX).partialFilterExpression(onlyAtsJobs));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260901_05 rollback – dropping ATS sync job indexes");
		for (String idx : new String[]{CONNECTION_STATUS_IDX, TENANT_CONNECTION_IDX}) {
			try {
				db.getCollection(COLLECTION).dropIndex(idx);
			} catch (Exception e) {
				log.warn("V20260901_05 rollback – could not drop index {}: {}", idx, e.getMessage());
			}
		}
	}
}
