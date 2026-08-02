package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChangeUnit(id = "V20260719_02__UpdateTenantsCollection", order = "20260719_02", author = "qorva")
public class V2026071902UpdateTenantsCollection extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260719_02__update_tenants_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260719_02 – adding recruitmentType/organizationSize to tenants schema validator");
		updateCollection(db, DDL_FILE, "V20260719_02 – tenants schema validator updated");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260719_02 rollback – reverting tenants schema validator");
		db.runCommand(new Document("collMod", "tenants")
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
	}
}
