package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Cross-tenant lists of the admin console: background jobs and Stripe event logs, newest first, filtered by type/status. */
@Slf4j
@Component
@ChangeUnit(id = "V20261010_06__AddAdminListIndexes", order = "20261010_06", author = "qorva")
public class V2026101006AddAdminListIndexes extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		var jobs = db.getCollection("background_jobs");
		jobs.createIndex(new Document("createdAt", -1), new IndexOptions().name("admin_jobs_created_idx"));
		jobs.createIndex(new Document("type", 1).append("createdAt", -1), new IndexOptions().name("admin_jobs_type_created_idx"));
		jobs.createIndex(new Document("status", 1).append("createdAt", -1), new IndexOptions().name("admin_jobs_status_created_idx"));
		var events = db.getCollection("stripe_event_logs");
		events.createIndex(new Document("createdAt", -1), new IndexOptions().name("admin_stripe_created_idx"));
		events.createIndex(new Document("eventType", 1).append("createdAt", -1), new IndexOptions().name("admin_stripe_type_created_idx"));
		events.createIndex(new Document("tenantId", 1).append("createdAt", -1), new IndexOptions().name("admin_stripe_tenant_created_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		var jobs = db.getCollection("background_jobs");
		jobs.dropIndex("admin_jobs_created_idx");
		jobs.dropIndex("admin_jobs_type_created_idx");
		jobs.dropIndex("admin_jobs_status_created_idx");
		var events = db.getCollection("stripe_event_logs");
		events.dropIndex("admin_stripe_created_idx");
		events.dropIndex("admin_stripe_type_created_idx");
		events.dropIndex("admin_stripe_tenant_created_idx");
	}
}
