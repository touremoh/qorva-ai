package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adds outdated marking, score history and input fingerprints to the matching_reports validator. Existing
 * reports need no backfill: no fingerprint means "regenerate on the next run", no outdated flag means current.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261003_02__UpdateMatchingReportsCollectionOutdated", order = "20261003_02", author = "qorva")
public class V2026100302UpdateMatchingReportsCollectionOutdated extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261003_02__update_matching_reports_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20260514_05__create_matching_reports_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261003_02 – matching_reports schema validator: outdated marking and fingerprints");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261003_02 rollback – restoring the previous matching_reports schema validator");
	}
}
