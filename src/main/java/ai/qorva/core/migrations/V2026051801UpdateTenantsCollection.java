package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChangeUnit(id = "V20260517_02__UpdateTenantsCollection", order = "20260517_02", author = "qorva")
public class V2026051801UpdateTenantsCollection extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260518_01__update_tenants_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260517_02 – updating tenants schema validator with new fields");
		updateCollection(db, DDL_FILE, "V20260517_02 – tenants schema validator updated");

		log.info("V20260517_02 – creating unique sparse index on tenants.organizationId");
		db.getCollection("tenants").createIndex(
			Indexes.ascending("organizationId"),
			new IndexOptions().unique(true).sparse(true).name("unique_organization_id")
		);
		log.info("V20260517_02 – unique_organization_id index created");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260517_02 rollback – reverting tenants schema validator");
		db.runCommand(new Document("collMod", "tenants")
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
		try {
			db.getCollection("tenants").dropIndex("unique_organization_id");
		} catch (Exception ignored) {}
	}
}
