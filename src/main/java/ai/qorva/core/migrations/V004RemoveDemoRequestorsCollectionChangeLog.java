package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "V004RemoveDemoRequestorsCollectionChangeLog", order = "004", author = "qorva")
public class V004RemoveDemoRequestorsCollectionChangeLog extends AbstractQorvaDbMigration {

	protected static final String COLLECTION_NAME = "DemoRequestors";

	@Execution
	public void execute(MongoDatabase db) {
		this.dropCollection(db, COLLECTION_NAME);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.error("Change Log ID = (V004RemoveDemoRequestorsCollectionChangeLog) - execution failed");
	}
}
