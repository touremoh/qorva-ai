package ai.qorva.core.migrations;

import ai.qorva.core.utils.ContactNormalizer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Duplicate detection compares normalised contact keys instead of the text as extracted:
 * declares {@code contactKeys} in the cvs validator, backfills it for every existing CV with the
 * same {@link ContactNormalizer} the write path uses, and indexes it per tenant.
 * <p>
 * Idempotent: the backfill recomputes and overwrites, so a re-run (or a partial run) converges.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260928_01__NormaliseContactKeys", order = "20260928_01", author = "qorva")
public class V2026092801NormaliseContactKeys extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260928_01__update_cvs_collection.json";
	static final String EMAIL_KEY_INDEX = "tenant_contact_email_key_idx";
	static final String PHONE_KEY_INDEX = "tenant_contact_phone_key_idx";
	private static final int BATCH = 1_000;

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260928_01 – declaring contactKeys, backfilling and indexing it");
		updateCollection(db, DDL_FILE, "V20260928_01 – cvs schema validator updated");

		var cvs = db.getCollection("cvs");
		long updated = backfill(cvs);
		log.info("V20260928_01 – contact keys backfilled: {} CVs", updated);

		cvs.createIndex(new Document("tenantId", 1).append("contactKeys.email", 1),
			new IndexOptions().name(EMAIL_KEY_INDEX));
		cvs.createIndex(new Document("tenantId", 1).append("contactKeys.phone", 1),
			new IndexOptions().name(PHONE_KEY_INDEX));
		log.info("V20260928_01 – contact key indexes created");
	}

	/** Recomputes contactKeys for every CV that has a contact; returns how many documents were written. */
	static long backfill(MongoCollection<Document> cvs) {
		long written = 0;
		var batch = new ArrayList<WriteModel<Document>>(BATCH);
		var cursor = cvs.find(Filters.exists("personalInformation.contact"))
			.projection(Projections.include("personalInformation.contact"))
			.batchSize(BATCH);
		for (var cv : cursor) {
			var contact = cv.getEmbedded(List.of("personalInformation", "contact"), Document.class);
			var address = contact != null ? contact.get("address", Document.class) : null;
			var keys = ContactNormalizer.keysOf(
				contact != null ? contact.getString("email") : null,
				contact != null ? contact.getString("phone") : null,
				address != null ? address.getString("country") : null);
			var update = keys == null
				? Updates.unset("contactKeys")
				: Updates.set("contactKeys", new Document("email", keys.getEmail()).append("phone", keys.getPhone()));
			batch.add(new UpdateOneModel<>(Filters.eq("_id", cv.get("_id")), update));
			if (batch.size() == BATCH) {
				written += cvs.bulkWrite(batch).getModifiedCount();
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			written += cvs.bulkWrite(batch).getModifiedCount();
		}
		return written;
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260928_01 rollback – dropping contact key indexes, removing contactKeys, restoring the validator");
		var cvs = db.getCollection("cvs");
		for (String idx : new String[]{EMAIL_KEY_INDEX, PHONE_KEY_INDEX}) {
			try {
				cvs.dropIndex(idx);
			} catch (Exception e) {
				log.warn("V20260928_01 rollback – could not drop index {}: {}", idx, e.getMessage());
			}
		}
		cvs.updateMany(Filters.exists("contactKeys"), Updates.unset("contactKeys"));
		updateCollection(db, "20260912_02__update_cvs_collection.json",
			"V20260928_01 rollback – cvs validator restored to V20260912_02");
	}
}
