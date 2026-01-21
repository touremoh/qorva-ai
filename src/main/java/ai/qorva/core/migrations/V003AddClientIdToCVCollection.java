package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "V003AddClientIdToCVCollection", order = "003", author = "qorva")
public class V003AddClientIdToCVCollection extends AbstractQorvaDbMigration {

	private static final String FILE_NAME = "V003_UpdateCVs_AddClientId.json";
	private static final String EXECUTION_MESSAGE = "Change Log ID = (addClientIdToCVCollection) - Adding clientId field to CVs collection";

	@Execution
	public void execute(MongoDatabase db) {
		this.updateCollection(db, FILE_NAME, EXECUTION_MESSAGE);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.error("Change Log ID = (addClientIdToCVCollection)  - execution failed");
	}
}
