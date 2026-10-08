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
 * Indexes for the new rule triggers' queries: reports never moved out of New, by creation (those moved since are
 * served by {@code pipeline_status_changed_idx}), closed jobs by the time they closed, and completed profile updates.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261008_02__AddRuleTriggerIndexes", order = "20261008_02", author = "qorva")
public class V2026100802AddRuleTriggerIndexes extends AbstractQorvaDbMigration {

	static final String REPORTS_NEW = "tenant_status_created_idx";
	static final String JOBS_CLOSED = "tenant_status_status_changed_idx";
	static final String UPDATES_COMPLETED = "tenant_status_completed_idx";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20261008_02 – indexes for the new rule triggers");
		db.getCollection("matching_reports").createIndex(
			new Document("tenantId", 1).append("status", 1).append("createdAt", 1), new IndexOptions().name(REPORTS_NEW));
		db.getCollection("job_posts").createIndex(
			new Document("tenantId", 1).append("status", 1).append("statusChangedAt", 1), new IndexOptions().name(JOBS_CLOSED));
		db.getCollection("candidate_update_requests").createIndex(
			new Document("tenantId", 1).append("status", 1).append("completedAt", 1), new IndexOptions().name(UPDATES_COMPLETED));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20261008_02 rollback – dropping the rule trigger indexes");
		db.getCollection("matching_reports").dropIndex(REPORTS_NEW);
		db.getCollection("job_posts").dropIndex(JOBS_CLOSED);
		db.getCollection("candidate_update_requests").dropIndex(UPDATES_COMPLETED);
	}
}
