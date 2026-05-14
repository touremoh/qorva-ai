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
 * Creates the users collection (snake_case, lowercase) and applies
 * the JSON schema validator defined in 20260514_02__create_users_collection.json.
 *
 * If the collection already exists under the old name "Users", rename it first
 * in DataGrip:
 *   db.adminCommand({ renameCollection: "<db>.Users", to: "<db>.users" })
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260514_02__CreateUsersCollection", order = "20260514_02", author = "qorva")
public class V2026051402CreateUsersCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "users";
	private static final String DDL_FILE = "20260514_02__create_users_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260514_02 – ensuring users collection exists and applying schema validator");

		boolean exists = db.listCollectionNames()
			.into(new ArrayList<>())
			.contains(COLLECTION);

		if (!exists) {
			db.createCollection(COLLECTION);
			log.info("V20260514_02 – users collection created");
		}

		updateCollection(db, DDL_FILE, "V20260514_02 – users schema validator applied");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260514_02 rollback – removing schema validator from users collection");
		db.runCommand(new Document("collMod", COLLECTION)
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
	}
}
