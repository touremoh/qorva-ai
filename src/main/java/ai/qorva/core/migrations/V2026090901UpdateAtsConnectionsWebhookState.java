package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adds webhookState to the ats_connections validator: the subscription ids Qorva registered
 * with a provider, the callback URL they point at, and the last registration error.
 *
 * <p>The validator runs in strict/error mode, so a connection carrying the new field would
 * be rejected on save until the schema knows about it. Nothing needs backfilling — existing
 * connections have no registered webhooks, and an absent webhookState reads as exactly
 * that.</p>
 *
 * <p>The signing key some providers hand back is deliberately not here. It is a secret, so
 * it goes into the encrypted credentials blob with everything else.</p>
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260909_01__UpdateAtsConnectionsWebhookState", order = "20260909_01", author = "qorva")
public class V2026090901UpdateAtsConnectionsWebhookState extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260909_01__update_ats_connections_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20260909_01 – ats_connections validator accepts webhookState");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260909_01 rollback – restoring the V20260906_01 ats_connections validator");
		updateCollection(db, "20260906_01__update_ats_connections_collection.json",
			"V20260909_01 rollback – ats_connections validator restored");
	}
}
