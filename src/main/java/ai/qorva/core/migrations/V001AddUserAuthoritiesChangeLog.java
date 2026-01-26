package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "V001AddUserAuthoritiesChangeLog", order = "001", author = "qorva")
public class V001AddUserAuthoritiesChangeLog extends AbstractQorvaDbMigration {

	private static final String FILE_NAME = "V001_UpdateUsers_AddAuthorities.json";
	private static final String EXECUTION_MESSAGE = "Change Log ID = (V001AddUserAuthoritiesChangeLog) - Adding permissions field to Users collection";

	@Execution
	public void execute(MongoDatabase db) {
		this.updateCollection(db, FILE_NAME, EXECUTION_MESSAGE);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.error("Change Log ID = (V001AddUserAuthoritiesChangeLog) - addUserAuthoritiesChangeLog execution failed");
	}
}
