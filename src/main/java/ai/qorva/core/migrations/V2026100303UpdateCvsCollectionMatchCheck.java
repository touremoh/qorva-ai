package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * Adds the matching staleness queue marks to the cvs validator, with a partial index covering only the
 * queued CVs — the sweep's query, oldest first. Existing CVs are not queued.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261003_03__UpdateCvsCollectionMatchCheck", order = "20261003_03", author = "qorva")
public class V2026100303UpdateCvsCollectionMatchCheck extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261003_03__update_cvs_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20260928_01__update_cvs_collection.json";
	static final String INDEX = "match_check_pending_idx";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261003_03 – cvs schema validator: matching staleness queue");
		db.getCollection("cvs").createIndex(new Document("matchCheckPending", 1).append("matchCheckPendingSince", 1),
			new IndexOptions().name(INDEX).partialFilterExpression(new Document("matchCheckPending", true)));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		db.getCollection("cvs").dropIndex(INDEX);
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261003_03 rollback – restoring the previous cvs schema validator");
	}
}
