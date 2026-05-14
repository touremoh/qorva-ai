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
 * Creates the stripe_product_references collection (snake_case, lowercase) and applies
 * the JSON schema validator defined in 20260514_09__create_stripe_product_references_collection.json.
 *
 * If the collection already exists under the old name "ProductsReferences", rename it first
 * in DataGrip:
 *   db.adminCommand({ renameCollection: "<db>.ProductsReferences", to: "<db>.stripe_product_references" })
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260514_09__CreateStripeProductReferencesCollection", order = "20260514_09", author = "qorva")
public class V2026051409CreateStripeProductReferencesCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "stripe_product_references";
	private static final String DDL_FILE = "20260514_09__create_stripe_product_references_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260514_09 – ensuring stripe_product_references collection exists and applying schema validator");

		boolean exists = db.listCollectionNames()
			.into(new ArrayList<>())
			.contains(COLLECTION);

		if (!exists) {
			db.createCollection(COLLECTION);
			log.info("V20260514_09 – stripe_product_references collection created");
		}

		updateCollection(db, DDL_FILE, "V20260514_09 – stripe_product_references schema validator applied");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260514_09 rollback – removing schema validator from stripe_product_references collection");
		db.runCommand(new Document("collMod", COLLECTION)
			.append("validator", new Document())
			.append("validationLevel", "off")
		);
	}
}
