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
 * Tenants get an account status (ACTIVE/SUSPENDED/DELETED, set from the admin console), the soft-delete dates and the
 * test-account fields. Existing tenants are backfilled ACTIVE; the code also reads an absent status as ACTIVE.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261010_04__UpdateTenantsCollectionStatus", order = "20261010_04", author = "qorva")
public class V2026101004UpdateTenantsCollectionStatus extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "tenants";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, "20261010_04__update_tenants_collection_status.json", "V20261010_04 – tenants schema validator: status, test accounts");
		var tenants = db.getCollection(COLLECTION);
		var backfilled = tenants.updateMany(new Document("status", new Document("$exists", false)),
			new Document("$set", new Document("status", "ACTIVE"))).getModifiedCount();
		log.info("V20261010_04 – {} tenant(s) backfilled with status ACTIVE", backfilled);
		tenants.createIndex(new Document("status", 1).append("purgeAfter", 1), new IndexOptions().name("tenants_status_purge_idx"));
		tenants.createIndex(new Document("accountType", 1).append("accessExpiresAt", 1), new IndexOptions().name("tenants_account_type_expiry_idx"));
		tenants.createIndex(new Document("createdAt", -1), new IndexOptions().name("tenants_created_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, "20261005_03__update_tenants_collection.json", "V20261010_04 rollback – restoring the previous tenants validator");
		var tenants = db.getCollection(COLLECTION);
		tenants.dropIndex("tenants_status_purge_idx");
		tenants.dropIndex("tenants_account_type_expiry_idx");
		tenants.dropIndex("tenants_created_idx");
	}
}
