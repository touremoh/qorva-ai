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
 * Declares atsRefs in the cvs validator and indexes the (provider, externalId) pair the
 * ATS sync uses as its idempotency key — findLinkedCv runs once per imported candidate,
 * so it must never scan the library.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260901_03__UpdateCvsCollection", order = "20260901_03", author = "qorva")
public class V2026090103UpdateCvsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "cvs";
	private static final String DDL_FILE = "20260901_03__update_cvs_collection.json";
	private static final String INDEX = "tenant_ats_ref_idx";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260901_03 – adding atsRefs to cvs schema and the ATS lookup index");
		updateCollection(db, DDL_FILE, "V20260901_03 – cvs schema validator updated");

		db.getCollection(COLLECTION).createIndex(
			new Document("tenantId", 1).append("atsRefs.provider", 1).append("atsRefs.externalId", 1),
			new IndexOptions().name(INDEX)
				// Only ATS-imported CVs carry refs; keep the index off the rest of the library.
				.partialFilterExpression(new Document("atsRefs.provider", new Document("$exists", true))));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260901_03 rollback – dropping {} and restoring the previous cvs validator", INDEX);
		try {
			db.getCollection(COLLECTION).dropIndex(INDEX);
		} catch (Exception e) {
			log.warn("V20260901_03 rollback – could not drop index {}: {}", INDEX, e.getMessage());
		}
		updateCollection(db, "20260727_01__update_cvs_collection.json",
			"V20260901_03 rollback – cvs validator restored to V20260727_01");
	}
}
