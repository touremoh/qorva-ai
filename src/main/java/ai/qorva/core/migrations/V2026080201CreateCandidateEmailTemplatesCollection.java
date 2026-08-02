package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Recruiter-authored invitation templates for candidate-update campaigns. */
@Slf4j
@Component
@ChangeUnit(id = "V20260802_01__CreateCandidateEmailTemplatesCollection", order = "20260802_01", author = "qorva")
public class V2026080201CreateCandidateEmailTemplatesCollection extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260802_01 – creating candidate_email_templates collection");
		db.createCollection("candidate_email_templates");
		db.getCollection("candidate_email_templates").createIndex(
			new Document("tenantId", 1).append("name", 1),
			new IndexOptions().unique(true).name("tenant_name_idx"));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260802_01 rollback – dropping candidate_email_templates");
		dropCollection(db, "candidate_email_templates");
	}
}
