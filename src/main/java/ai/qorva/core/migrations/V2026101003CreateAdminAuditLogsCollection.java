package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Every change made from the admin console. Kept when a tenant is purged. */
@Slf4j
@Component
@ChangeUnit(id = "V20261010_03__CreateAdminAuditLogsCollection", order = "20261010_03", author = "qorva")
public class V2026101003CreateAdminAuditLogsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "admin_audit_logs";

	@Execution
	public void execute(MongoDatabase db) {
		createCollectionIfAbsent(db, COLLECTION);
		var logs = db.getCollection(COLLECTION);
		logs.createIndex(new Document("at", -1), new IndexOptions().name("admin_audit_at_idx"));
		logs.createIndex(new Document("targetTenantId", 1).append("at", -1), new IndexOptions().name("admin_audit_tenant_at_idx"));
		logs.createIndex(new Document("adminId", 1).append("at", -1), new IndexOptions().name("admin_audit_admin_at_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20261010_03 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
