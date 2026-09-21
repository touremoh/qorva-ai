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
 * Candidate outreach log: one document per email a recruiter sent to a candidate (through a
 * connected mailbox) or handed off to their own mail client. A separate collection so that
 * contacting a candidate never touches {@code cvs} (which would re-flag open jobs for screening).
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260919_02__CreateCandidateOutreachCollection", order = "20260919_02", author = "qorva")
public class V2026091902CreateCandidateOutreachCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "candidate_outreach";
	private static final String DDL_FILE = "20260919_02__create_candidate_outreach_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20260919_02 – creating candidate_outreach collection", DDL_FILE);
		// The one read path: a candidate's contact history, newest first.
		db.getCollection(COLLECTION).createIndex(
			new Document("tenantId", 1).append("cvId", 1).append("createdAt", -1),
			new IndexOptions().name("tenant_cv_created_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260919_02 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
