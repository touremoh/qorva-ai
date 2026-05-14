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
 * Creates the job_posts collection (snake_case, lowercase) and applies
 * the JSON schema validator defined in 20260514_04__create_job_posts_collection.json.
 *
 * If the collection already exists under the old name "JobsPosts", rename it first
 * in DataGrip:
 *   db.adminCommand({ renameCollection: "<db>.JobsPosts", to: "<db>.job_posts" })
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260514_04__CreateJobPostsCollection", order = "20260514_04", author = "qorva")
public class V2026051404CreateJobPostsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "job_posts";
	private static final String DDL_FILE = "20260514_04__create_job_posts_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260514_04 – ensuring job_posts collection exists and applying schema validator");

		boolean exists = db.listCollectionNames()
			.into(new ArrayList<>())
			.contains(COLLECTION);

		if (!exists) {
			db.createCollection(COLLECTION);
			log.info("V20260514_04 – job_posts collection created");
		}

		updateCollection(db, DDL_FILE, "V20260514_04 – job_posts schema validator applied");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260514_04 rollback – removing schema validator from job_posts collection");
		db.runCommand(new Document("collMod", COLLECTION)
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
	}
}
