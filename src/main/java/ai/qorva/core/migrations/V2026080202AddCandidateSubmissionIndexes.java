package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Worker claim index for asynchronous candidate submissions. */
@Slf4j
@Component
@ChangeUnit(id = "V20260802_02__AddCandidateSubmissionIndexes", order = "20260802_02", author = "qorva")
public class V2026080202AddCandidateSubmissionIndexes extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260802_02 – adding candidate submission worker claim index");
		db.getCollection("candidate_update_requests").createIndex(
			new Document("status", 1).append("leaseExpiresAt", 1),
			new IndexOptions().name("status_lease_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260802_02 rollback – dropping status_lease_idx");
		try {
			db.getCollection("candidate_update_requests").dropIndex("status_lease_idx");
		} catch (Exception e) {
			log.warn("Index drop skipped: {}", e.getMessage());
		}
	}
}
