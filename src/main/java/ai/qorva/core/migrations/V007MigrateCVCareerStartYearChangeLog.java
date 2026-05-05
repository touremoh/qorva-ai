package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 1. Updates the CVs collection JSON schema validator: replaces nbYearsOfExperience with careerStartYear.
 * 2. Migrates existing documents: careerStartYear = currentYear - nbYearsOfExperience.
 */
@Slf4j
@Component
@ChangeUnit(id = "V007MigrateCVCareerStartYearChangeLog", order = "007", author = "qorva")
public class V007MigrateCVCareerStartYearChangeLog extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "CVs";
	private static final String OLD_FIELD = "nbYearsOfExperience";
	private static final String NEW_FIELD = "careerStartYear";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V007 – updating CVs validator and migrating {} → {}", OLD_FIELD, NEW_FIELD);

		updateValidator(db, OLD_FIELD, NEW_FIELD);

		var result = db.getCollection(COLLECTION).updateMany(
			Filters.and(Filters.exists(OLD_FIELD), Filters.exists(NEW_FIELD, false)),
			List.of(
				new Document("$set", new Document(NEW_FIELD,
					new Document("$subtract", List.of(new Document("$year", "$$NOW"), "$" + OLD_FIELD))
				)),
				new Document("$unset", OLD_FIELD)
			)
		);
		log.info("V007 – migrated {} documents", result.getModifiedCount());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V007 rollback – restoring validator and reverting {} → {}", NEW_FIELD, OLD_FIELD);

		updateValidator(db, NEW_FIELD, OLD_FIELD);

		var result = db.getCollection(COLLECTION).updateMany(
			Filters.and(Filters.exists(NEW_FIELD), Filters.exists(OLD_FIELD, false)),
			List.of(
				new Document("$set", new Document(OLD_FIELD,
					new Document("$subtract", List.of(new Document("$year", "$$NOW"), "$" + NEW_FIELD))
				)),
				new Document("$unset", NEW_FIELD)
			)
		);
		log.warn("V007 rollback – reverted {} documents", result.getModifiedCount());
	}

	private void updateValidator(MongoDatabase db, String from, String to) {
		var collInfo = db.listCollections().filter(Filters.eq("name", COLLECTION)).first();
		if (collInfo == null) return;

		var options = collInfo.get("options", Document.class);
		if (options == null) return;

		var validator = options.get("validator", Document.class);
		if (validator == null) return;

		var jsonSchema = validator.get("$jsonSchema", Document.class);
		if (jsonSchema == null) return;

		// Swap field name in required array
		var required = jsonSchema.getList("required", String.class);
		if (required != null) {
			int idx = required.indexOf(from);
			if (idx != -1) {
				required.set(idx, to);
			}
		}

		// Swap field name in properties map
		var properties = jsonSchema.get("properties", Document.class);
		if (properties != null && properties.containsKey(from)) {
			properties.put(to, properties.get(from, Document.class));
			properties.remove(from);
		}

		db.runCommand(new Document("collMod", COLLECTION)
			.append("validator", validator)
			.append("validationAction", "error")
			.append("validationLevel", "strict")
		);
		log.info("V007 – validator updated: {} → {}", from, to);
	}
}
