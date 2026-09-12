package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Declares searchIndex.locations in the cvs validator: the English place names (city, region,
 * country, continent) a talent intelligence location filter reads. The raw contact address stays
 * in the CV's language and is a nested document, so no text filter could ever match it.
 * <p>
 * No data backfill — CVs extracted before this change simply carry no locations and stay
 * invisible to location-filtered questions until they are re-uploaded, which is the same
 * behaviour they had before.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260912_02__UpdateCvsCollection", order = "20260912_02", author = "qorva")
public class V2026091202UpdateCvsCollection extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260912_02__update_cvs_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260912_02 – adding searchIndex.locations to the cvs schema");
		updateCollection(db, DDL_FILE, "V20260912_02 – cvs schema validator updated");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260912_02 rollback – restoring the previous cvs validator");
		updateCollection(db, "20260901_03__update_cvs_collection.json",
			"V20260912_02 rollback – cvs validator restored to V20260901_03");
	}
}
