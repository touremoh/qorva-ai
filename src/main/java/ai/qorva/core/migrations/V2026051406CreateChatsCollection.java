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
 * Creates the chats collection (snake_case, lowercase) and applies
 * the JSON schema validator defined in 20260514_06__create_chats_collection.json.
 *
 * If the collection already exists under the old name "Chats", rename it first
 * in DataGrip:
 *   db.adminCommand({ renameCollection: "<db>.Chats", to: "<db>.chats" })
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260514_06__CreateChatsCollection", order = "20260514_06", author = "qorva")
public class V2026051406CreateChatsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "chats";
	private static final String DDL_FILE = "20260514_06__create_chats_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260514_06 – ensuring chats collection exists and applying schema validator");

		boolean exists = db.listCollectionNames()
			.into(new ArrayList<>())
			.contains(COLLECTION);

		if (!exists) {
			db.createCollection(COLLECTION);
			log.info("V20260514_06 – chats collection created");
		}

		updateCollection(db, DDL_FILE, "V20260514_06 – chats schema validator applied");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260514_06 rollback – removing schema validator from chats collection");
		db.runCommand(new Document("collMod", COLLECTION)
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
	}
}
