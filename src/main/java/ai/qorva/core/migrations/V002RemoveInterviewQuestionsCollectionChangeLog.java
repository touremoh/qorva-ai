package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "V002RemoveInterviewQuestionsCollectionChangeLog", order = "002", author = "qorva")
public class V002RemoveInterviewQuestionsCollectionChangeLog extends AbstractQorvaDbMigration {

	protected static final String COLLECTION_NAME = "InterviewQuestions";

	@Execution
	public void execute(MongoDatabase db) {
		this.dropCollection(db, COLLECTION_NAME);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.error("Change Log ID = (V002RemoveInterviewQuestionsCollectionChangeLog) - execution failed");
	}
}
