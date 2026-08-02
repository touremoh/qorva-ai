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
 * Library Quality foundations: declares qualityFlags/archived in the cvs validator and
 * creates the indexes that keep quality reads (report, drill-downs, bulk-action criteria,
 * duplicates, ingest lookups) off collection scans at any library size.
 * No data backfill — flags are computed on write; existing data is wiped and re-uploaded
 * (decision 2026-07-27, see docs/quality-remediation-implementation-plan.md).
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260727_01__UpdateCvsCollection", order = "20260727_01", author = "qorva")
public class V2026072701UpdateCvsCollection extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260727_01__update_cvs_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260727_01 – adding qualityFlags/archived to cvs schema and quality indexes");
		updateCollection(db, DDL_FILE, "V20260727_01 – cvs schema validator updated");

		var cvs = db.getCollection("cvs");
		cvs.createIndex(new Document("tenantId", 1).append("qualityFlags", 1),
			new IndexOptions().name("tenant_quality_flags_idx"));
		cvs.createIndex(new Document("tenantId", 1).append("contentDate", 1),
			new IndexOptions().name("tenant_content_date_idx"));
		cvs.createIndex(new Document("tenantId", 1).append("personalInformation.contact.email", 1),
			new IndexOptions().name("tenant_contact_email_idx"));
		cvs.createIndex(new Document("tenantId", 1).append("personalInformation.contact.phone", 1),
			new IndexOptions().name("tenant_contact_phone_idx"));
		cvs.createIndex(new Document("tenantId", 1).append("archived", 1),
			new IndexOptions().name("tenant_archived_idx")
				.partialFilterExpression(new Document("archived", true)));
		log.info("V20260727_01 – quality indexes created");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260727_01 rollback – dropping quality indexes and disabling cvs validator");
		var cvs = db.getCollection("cvs");
		for (String idx : new String[]{"tenant_quality_flags_idx", "tenant_content_date_idx",
			"tenant_contact_email_idx", "tenant_contact_phone_idx", "tenant_archived_idx"}) {
			try {
				cvs.dropIndex(idx);
			} catch (Exception e) {
				log.warn("V20260727_01 rollback – could not drop index {}: {}", idx, e.getMessage());
			}
		}
		db.runCommand(new Document("collMod", "cvs")
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
	}
}
