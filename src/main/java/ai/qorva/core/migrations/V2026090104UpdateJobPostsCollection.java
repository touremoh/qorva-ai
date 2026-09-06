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
 * Declares atsRef in the job_posts validator and indexes the (provider, externalId) pair
 * the job import upserts on.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260901_04__UpdateJobPostsCollection", order = "20260901_04", author = "qorva")
public class V2026090104UpdateJobPostsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "job_posts";
	private static final String DDL_FILE = "20260901_04__update_job_posts_collection.json";
	private static final String INDEX = "tenant_ats_ref_idx";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260901_04 – adding atsRef to job_posts schema and the ATS lookup index");
		updateCollection(db, DDL_FILE, "V20260901_04 – job_posts schema validator updated");

		db.getCollection(COLLECTION).createIndex(
			new Document("tenantId", 1).append("atsRef.provider", 1).append("atsRef.externalId", 1),
			new IndexOptions().name(INDEX)
				// Only ATS-imported jobs carry a ref.
				.partialFilterExpression(new Document("atsRef.provider", new Document("$exists", true))));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260901_04 rollback – dropping {} and restoring the previous job_posts validator", INDEX);
		try {
			db.getCollection(COLLECTION).dropIndex(INDEX);
		} catch (Exception e) {
			log.warn("V20260901_04 rollback – could not drop index {}: {}", INDEX, e.getMessage());
		}
		updateCollection(db, "20260514_04__create_job_posts_collection.json",
			"V20260901_04 rollback – job_posts validator restored to V20260514_04");
	}
}
