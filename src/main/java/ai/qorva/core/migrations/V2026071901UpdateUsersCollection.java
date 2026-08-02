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
@ChangeUnit(id = "V20260719_01__UpdateUsersCollection", order = "20260719_01", author = "qorva")
public class V2026071901UpdateUsersCollection extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260719_01__update_users_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260719_01 – adding passwordCredentialVersion to users schema validator");
		updateCollection(db, DDL_FILE, "V20260719_01 – users schema validator updated");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260719_01 rollback – reverting users schema validator");
		db.runCommand(new Document("collMod", "users")
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
	}
}
