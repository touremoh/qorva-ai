package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** Admin console accounts (Qorva staff), separate from tenant users. */
@Slf4j
@Component
@ChangeUnit(id = "V20261010_01__CreatePlatformAdminsCollection", order = "20261010_01", author = "qorva")
public class V2026101001CreatePlatformAdminsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "platform_admins";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20261010_01 – creating platform_admins collection", "20261010_01__create_platform_admins_collection.json");
		db.getCollection(COLLECTION).createIndex(new Document("email", 1), new IndexOptions().name("platform_admins_email_uniq").unique(true));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20261010_01 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
