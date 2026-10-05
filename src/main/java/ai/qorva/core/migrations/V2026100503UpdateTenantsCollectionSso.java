package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Lets tenants hold {@code ssoRequired}. Existing tenants need nothing: absent means password sign-in stays allowed. */
@Slf4j
@Component
@ChangeUnit(id = "V20261005_03__UpdateTenantsCollectionSso", order = "20261005_03", author = "qorva")
public class V2026100503UpdateTenantsCollectionSso extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261005_03__update_tenants_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20260719_02__update_tenants_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261005_03 – tenants schema validator: ssoRequired");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261005_03 rollback – restoring the previous tenants schema validator");
	}
}
