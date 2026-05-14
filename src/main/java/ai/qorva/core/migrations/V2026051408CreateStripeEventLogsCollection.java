package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Creates the stripe_event_logs collection (snake_case, lowercase) and applies
 * the JSON schema validator defined in 20260514_08__create_stripe_event_logs_collection.json.
 *
 * If the collection already exists under the old name "StripeEventLogs", rename it first
 * in DataGrip:
 *   db.adminCommand({ renameCollection: "<db>.StripeEventLogs", to: "<db>.stripe_event_logs" })
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260514_08__CreateStripeEventLogsCollection", order = "20260514_08", author = "qorva")
public class V2026051408CreateStripeEventLogsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "stripe_event_logs";
	private static final String DDL_FILE = "20260514_08__create_stripe_event_logs_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260514_08 – ensuring stripe_event_logs collection exists and applying schema validator");

		boolean exists = db.listCollectionNames()
			.into(new ArrayList<>())
			.contains(COLLECTION);

		if (!exists) {
			db.createCollection(COLLECTION);
			log.info("V20260514_08 – stripe_event_logs collection created");
		}

		updateCollection(db, DDL_FILE, "V20260514_08 – stripe_event_logs schema validator applied");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260514_08 rollback – removing schema validator from stripe_event_logs collection");
		db.runCommand(new Document("collMod", COLLECTION)
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
	}
}
