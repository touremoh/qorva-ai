package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "V003RemoveDemoPartnersCollectionChangeLog", order = "003", author = "qorva")
public class V003RemoveDemoPartnersCollectionChangeLog extends AbstractQorvaDbMigration {

	protected static final String COLLECTION_NAME = "DemoPartners";

	@Execution
	public void execute(MongoDatabase db) {
		this.dropCollection(db, COLLECTION_NAME);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.error("Change Log ID = (V003RemoveDemoPartnersCollectionChangeLog) - execution failed");
	}
}
