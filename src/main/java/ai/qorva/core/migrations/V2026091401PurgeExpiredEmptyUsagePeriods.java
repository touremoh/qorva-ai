package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Removes usage_monitoring periods that carry no information: already expired, nothing
 * consumed, nothing carried forward. The scheduler used to copy a tenant's stale Stripe
 * period every five minutes — a period that was expired on arrival — which left hundreds of
 * such rows per tenant (777 on tst for one tenant). Periods with any consumption or cumulative
 * total are history and stay.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260914_01__PurgeExpiredEmptyUsagePeriods", order = "20260914_01", author = "qorva")
public class V2026091401PurgeExpiredEmptyUsagePeriods extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "usage_monitoring";

	@Execution
	public void execute(MongoDatabase db) {
		var result = db.getCollection(COLLECTION).deleteMany(filter(new Date()));
		log.info("V20260914_01 – removed {} expired usage periods with no consumption", result.getDeletedCount());
	}

	/** Expired before {@code now}, and every counter is zero or absent. Package-private for the test. */
	static Document filter(Date now) {
		// Arrays.asList, not List.of: a missing counter is matched with null, which List.of rejects.
		List<Object> zeroInt = Arrays.asList(0, null);
		List<Object> zeroLong = Arrays.asList(0, 0L, null);
		var zero = List.of(
			new Document("features.screeningActions.consumed", new Document("$in", zeroInt)),
			new Document("features.aiResumeChats.consumed", new Document("$in", zeroInt)),
			new Document("features.talentIntelligenceQueries.consumed", new Document("$in", zeroInt)),
			new Document("features.screeningActions.cumulative", new Document("$in", zeroLong)),
			new Document("features.aiResumeChats.cumulative", new Document("$in", zeroLong)),
			new Document("features.talentIntelligenceQueries.cumulative", new Document("$in", zeroLong)));
		return new Document("currentPeriodEnd", new Document("$lt", now)).append("$and", zero);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260914_01 rollback – nothing to restore (deleted periods carried no data)");
	}
}
