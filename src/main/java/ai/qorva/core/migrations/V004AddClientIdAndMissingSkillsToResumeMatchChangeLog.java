package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "V004AddClientIdAndMissingSkillsToResumeMatchChangeLog", order = "004", author = "qorva")
public class V004AddClientIdAndMissingSkillsToResumeMatchChangeLog extends AbstractQorvaDbMigration {
	private static final String FILE_NAME = "V004_UpdateResumeMatch_AddClientIdAndMissingSkillsList.json";
	private static final String EXECUTION_MESSAGE = "Change Log ID = (V004AddClientIdAndMissingSkillsToResumeMatchChangeLog) - Adding clientId and missingSkills fields to ResumeMatch collection";

	@Execution
	public void execute(MongoDatabase db) {
		this.updateCollection(db, FILE_NAME, EXECUTION_MESSAGE);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.error("Change Log ID = (V004AddClientIdAndMissingSkillsToResumeMatchChangeLog) - execution failed");
	}
}
