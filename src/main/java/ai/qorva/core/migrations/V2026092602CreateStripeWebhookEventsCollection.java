package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * The Stripe webhook ledger: one row per received event id, claimed before the event is handled,
 * so a replayed or concurrently re-delivered event is processed once. Rows expire after 30 days.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260926_02__CreateStripeWebhookEventsCollection", order = "20260926_02", author = "qorva")
public class V2026092602CreateStripeWebhookEventsCollection extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "stripe_webhook_events";
	private static final String DDL_FILE = "20260926_02__create_stripe_webhook_events_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, COLLECTION, "V20260926_02 – creating stripe_webhook_events collection", DDL_FILE);
		db.getCollection(COLLECTION).createIndex(
			new Document("createdAt", 1),
			new IndexOptions().name("stripe_webhook_events_ttl_idx").expireAfter(30L, TimeUnit.DAYS));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260926_02 rollback – dropping {} collection", COLLECTION);
		dropCollection(db, COLLECTION);
	}
}
