package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Per-tenant dismiss/reopen triage state for Library Quality issues. */
@Slf4j
@Component
@ChangeUnit(id = "V20260727_02__CreateQualityIssueStatesCollection", order = "20260727_02", author = "qorva")
public class V2026072702CreateQualityIssueStatesCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "quality_issue_states";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260727_02 – creating {} collection", COLLECTION);
		db.createCollection(COLLECTION);
		db.getCollection(COLLECTION).createIndex(
			new Document("tenantId", 1).append("issueKey", 1),
			new IndexOptions().unique(true).name("tenant_issue_key_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260727_02 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
