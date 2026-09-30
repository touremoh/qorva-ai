package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Adds the agentRuns meter to the usage periods that are current when Copilot ships. Without it
 * those tenants would be unmetered until their period rolls over (a missing limit means unlimited).
 * New periods get the meter from the plan configuration.
 *
 * <p>Limits are the plan values of 2026-09-30 (application.yml, qorva.products.*.features.limits.agent-runs),
 * frozen here on purpose: a changeunit must not change meaning when the configuration does. Yearly
 * periods (longer than 45 days) get twelve months' worth, like UsageMonitoringScheduler.scaleFeatures.
 * Only periods without the meter are touched, so the change is idempotent.</p>
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260930_03__BackfillAgentRunsUsage", order = "20260930_03", author = "qorva")
public class V2026093003BackfillAgentRunsUsage extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "usage_monitoring";
	private static final Map<String, Integer> MONTHLY_LIMITS = Map.of("starter", 100, "pro", 500, "scale", 2000);
	private static final Duration YEARLY_THRESHOLD = Duration.ofDays(45);

	@Execution
	public void execute(MongoDatabase db) {
		var now = new Date();
		var current = new Document("$and", List.of(
			new Document("currentPeriodStart", new Document("$lte", now)),
			new Document("currentPeriodEnd", new Document("$gt", now)),
			new Document("features.agentRuns", new Document("$exists", false))));
		var usage = db.getCollection(COLLECTION);
		int updated = 0;
		int skipped = 0;
		for (var period : usage.find(current)) {
			var tier = period.getString("subscriptionTier");
			var monthly = tier != null ? MONTHLY_LIMITS.get(tier.trim().toLowerCase()) : null;
			if (monthly == null) {
				skipped++;
				continue;
			}
			var start = period.getDate("currentPeriodStart").toInstant();
			var end = period.getDate("currentPeriodEnd").toInstant();
			int limit = Duration.between(start, end).compareTo(YEARLY_THRESHOLD) > 0 ? monthly * 12 : monthly;
			var meter = new Document("limit", limit).append("consumed", 0).append("cumulative", 0L);
			usage.updateOne(new Document("_id", period.getObjectId("_id")).append("features.agentRuns", new Document("$exists", false)),
				new Document("$set", new Document("features.agentRuns", meter)));
			updated++;
		}
		log.info("V20260930_03 – agentRuns meter added to {} current periods ({} with an unknown tier left unmetered) at {}",
			updated, skipped, Instant.now());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260930_03 rollback – removing the agentRuns meter");
		db.getCollection(COLLECTION).updateMany(new Document("features.agentRuns", new Document("$exists", true)),
			new Document("$unset", new Document("features.agentRuns", "")));
	}
}
