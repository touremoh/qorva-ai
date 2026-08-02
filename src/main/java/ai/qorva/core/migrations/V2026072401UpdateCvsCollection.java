package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * The attachment field becomes an S3 reference object (originals are no longer stored in MongoDB),
 * and the CV gains rawText + contentDate/contentDateSource for re-analysis and content-based freshness.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260724_01__UpdateCvsCollection", order = "20260724_01", author = "qorva")
public class V2026072401UpdateCvsCollection extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260724_01__update_cvs_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260724_01 – replacing binary attachment with S3 reference and adding rawText/contentDate to cvs schema");

		// Any legacy binary attachments would violate the new object-typed validator — clear them first.
		var unsetResult = db.getCollection("cvs").updateMany(
			new Document("attachment", new Document("$type", "binData")),
			new Document("$unset", new Document("attachment", ""))
		);
		log.info("V20260724_01 – cleared legacy binary attachment on {} documents", unsetResult.getModifiedCount());

		updateCollection(db, DDL_FILE, "V20260724_01 – cvs schema validator updated");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260724_01 rollback – disabling cvs schema validator");
		db.runCommand(new Document("collMod", "cvs")
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
	}
}
