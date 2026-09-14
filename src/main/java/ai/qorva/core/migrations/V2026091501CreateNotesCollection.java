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
 * Recruiter notes on CVs and matching reports. A separate collection so that writing a note never
 * touches {@code cvs} (which would re-flag open jobs for screening) and never rides along when the
 * CV document is serialised into an AI prompt.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260915_01__CreateNotesCollection", order = "20260915_01", author = "qorva")
public class V2026091501CreateNotesCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "notes";
	private static final String DDL_FILE = "20260915_01__create_notes_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20260915_01 – creating notes collection", DDL_FILE);
		// The one read path: a target's thread, newest first.
		db.getCollection(COLLECTION).createIndex(
			new Document("tenantId", 1).append("targetType", 1).append("targetId", 1).append("createdAt", -1),
			new IndexOptions().name("tenant_target_created_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260915_01 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
