package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Candidate self-update requests (tokenized public form) + do-not-contact suppression list. */
@Slf4j
@Component
@ChangeUnit(id = "V20260727_04__CreateCandidateUpdateCollections", order = "20260727_04", author = "qorva")
public class V2026072704CreateCandidateUpdateCollections extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260727_04 – creating candidate_update_requests and suppressed_emails collections");
		db.createCollection("candidate_update_requests");
		var requests = db.getCollection("candidate_update_requests");
		requests.createIndex(new Document("tokenHash", 1), new IndexOptions().unique(true).name("token_hash_idx"));
		requests.createIndex(new Document("tenantId", 1).append("cvId", 1).append("status", 1),
			new IndexOptions().name("tenant_cv_status_idx"));

		db.createCollection("suppressed_emails");
		db.getCollection("suppressed_emails").createIndex(
			new Document("tenantId", 1).append("email", 1),
			new IndexOptions().unique(true).name("tenant_email_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260727_04 rollback – dropping candidate update collections");
		dropCollection(db, "candidate_update_requests");
		dropCollection(db, "suppressed_emails");
	}
}
