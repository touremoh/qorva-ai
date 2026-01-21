package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "V002AddClientIdAndScoringConfigToJobsPosts", order = "002", author = "qorva")
public class V002AddClientIdAndScoringConfigToJobsPosts extends AbstractQorvaDbMigration {

	protected static final String FILE_NAME = "V002_UpdateJobsPosts_AddClientIdAndScoringCriteria.json";
	protected static final String EXECUTION_MESSAGE = "Change Log ID = (V002AddClientIdAndScoringConfigToJobsPosts) - Adding clientId and scoringRules fields to JobsPosts collection";

	@Execution
	public void execute(MongoDatabase db) {
		this.updateCollection(db, FILE_NAME, EXECUTION_MESSAGE);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.error("QORVA MIGRATION - V002AddClientIdAndScoringConfigToJobsPosts execution failed");
	}
}
