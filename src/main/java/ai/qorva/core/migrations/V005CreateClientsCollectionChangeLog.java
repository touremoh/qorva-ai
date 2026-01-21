package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "V005CreateClientsCollectionChangeLog", order = "005", author = "qorva")
public class V005CreateClientsCollectionChangeLog extends AbstractQorvaDbMigration {
	protected static final String FILE_NAME = "V005_CreateClientsCollection.json";
	protected static final String EXECUTION_MESSAGE = "Change Log ID = (V005CreateClientsCollectionChangeLog) - Create client collection";
	protected static final String COLLECTION_NAME = "Clients";

	@Execution
	public void execute(MongoDatabase db) {
		this.createCollection(db, COLLECTION_NAME, EXECUTION_MESSAGE, FILE_NAME);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.error("Change Log ID = (V005CreateClientsCollectionChangeLog) - execution failed");
	}

}
