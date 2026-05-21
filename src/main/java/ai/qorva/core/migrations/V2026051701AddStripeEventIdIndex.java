package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChangeUnit(id = "V20260517_01__AddStripeEventIdIndex", order = "20260517_01", author = "qorva")
public class V2026051701AddStripeEventIdIndex extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260517_01 – creating unique sparse index on stripe_event_logs.stripeEventId");
		db.getCollection("stripe_event_logs").createIndex(
			Indexes.ascending("stripeEventId"),
			new IndexOptions().unique(true).sparse(true).name("unique_stripe_event_id")
		);
		log.info("V20260517_01 – unique_stripe_event_id index created");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260517_01 rollback – dropping unique_stripe_event_id index");
		try {
			db.getCollection("stripe_event_logs").dropIndex("unique_stripe_event_id");
		} catch (Exception ignored) {}
	}
}
