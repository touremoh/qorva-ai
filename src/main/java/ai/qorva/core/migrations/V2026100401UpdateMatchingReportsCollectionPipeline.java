package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Candidate pipeline on matching reports: maps the statuses of the old enum (only NEW was ever written) onto the
 * pipeline ones, then restricts {@code status} to them and adds the status history to the validator. Idempotent.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261004_01__UpdateMatchingReportsCollectionPipeline", order = "20261004_01", author = "qorva")
public class V2026100401UpdateMatchingReportsCollectionPipeline extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261004_01__update_matching_reports_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20261003_02__update_matching_reports_collection.json";
	static final String STATUS_CHANGED_INDEX = "tenant_status_changed_idx";

	private static final Map<String, String> LEGACY = Map.of(
		"OPEN", "NEW",
		"CLOSE", "REJECTED",
		"SHORTLIST", "SHORTLISTED",
		"INTERVIEW", "INTERVIEWING",
		"REJECT", "REJECTED");

	@Execution
	public void execute(MongoDatabase db) {
		var reports = db.getCollection("matching_reports");
		LEGACY.forEach((from, to) -> {
			long n = reports.updateMany(new Document("status", from), new Document("$set", new Document("status", to)))
				.getModifiedCount();
			if (n > 0) {
				log.info("V20261004_01 – {} matching reports moved from status {} to {}", n, from, to);
			}
		});
		long missing = reports.updateMany(new Document("status", new Document("$nin", java.util.List.of(
				"NEW", "CONTACTED", "SHORTLISTED", "INTERVIEWING", "OFFERED", "HIRED", "REJECTED", "WITHDRAWN"))),
			new Document("$set", new Document("status", "NEW"))).getModifiedCount();
		if (missing > 0) {
			log.info("V20261004_01 – {} matching reports with an unknown status reset to NEW", missing);
		}
		updateCollection(db, DDL_FILE, "V20261004_01 – matching_reports schema validator: pipeline statuses and history");
		// Status-rule source (changes in a window) and the pipeline dashboard (per tenant).
		reports.createIndex(new Document("tenantId", 1).append("statusChangedAt", 1),
			new IndexOptions().name(STATUS_CHANGED_INDEX).sparse(true));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261004_01 rollback – restoring the previous matching_reports schema validator");
		try {
			db.getCollection("matching_reports").dropIndex(STATUS_CHANGED_INDEX);
		} catch (Exception e) {
			log.warn("V20261004_01 rollback – index {} not dropped: {}", STATUS_CHANGED_INDEX, e.getMessage());
		}
	}
}
