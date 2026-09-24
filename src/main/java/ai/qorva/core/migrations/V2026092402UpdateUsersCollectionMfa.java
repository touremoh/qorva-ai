package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Adds the optional email-MFA fields to the users validator. Existing documents need no backfill: absent = off. */
@Slf4j
@Component
@ChangeUnit(id = "V20260924_02__UpdateUsersCollectionMfa", order = "20260924_02", author = "qorva")
public class V2026092402UpdateUsersCollectionMfa extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260924_02__update_users_collection_mfa.json";
	private static final String PREVIOUS_DDL_FILE = "20260719_01__update_users_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260924_02 – adding mfaEnabled / mfaEnabledAt to users schema validator");
		updateCollection(db, DDL_FILE, "V20260924_02 – users schema validator updated");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260924_02 rollback – restoring the previous users schema validator");
		updateCollection(db, PREVIOUS_DDL_FILE, "V20260924_02 rollback – users schema validator restored");
	}
}
