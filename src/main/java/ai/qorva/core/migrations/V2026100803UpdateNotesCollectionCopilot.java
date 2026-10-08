package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Notes Copilot writes for its user: a {@code source} and the task id, and room for a whole answer (an interview
 * plan) — up to 12,000 characters; people's own notes stay capped at 4,000 by the API.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261008_03__UpdateNotesCollectionCopilot", order = "20261008_03", author = "qorva")
public class V2026100803UpdateNotesCollectionCopilot extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261008_03__update_notes_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20260915_01__create_notes_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261008_03 – notes schema validator: Copilot notes");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261008_03 rollback – restoring the previous notes schema validator");
	}
}
