package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Extends the MANAGE_INTEGRATIONS grant of V20260903_01 to account managers, who are
 * identified by the role carried on their own authorities (the user document has no role
 * field of its own). Kept as a separate changeunit so it applies whether or not the
 * previous one has already run — an executed changeunit is never re-run.
 *
 * <p>Demo accounts are excluded: their authority set is deliberately read-only.</p>
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260903_02__BackfillManagerIntegrationsAuthority", order = "20260903_02", author = "qorva")
public class V2026090302BackfillManagerIntegrationsAuthority extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "users";
	private static final String ACTION = "MANAGE_INTEGRATIONS";
	private static final String ROLE = "ACCOUNT_MANAGER";
	private static final String DEMO_STATUS = "DEMO";

	@Execution
	public void execute(MongoDatabase db) {
		var authority = new Document("role", ROLE)
			.append("action", ACTION)
			.append("permission", "ALLOWED");

		var target = new Document("$and", List.of(
			new Document("authorities.role", ROLE),
			new Document("authorities.action", new Document("$ne", ACTION)),
			new Document("userAccountStatus", new Document("$ne", DEMO_STATUS))));

		var result = db.getCollection(COLLECTION)
			.updateMany(target, new Document("$push", new Document("authorities", authority)));

		log.info("V20260903_02 – granted {} to {} account managers", ACTION, result.getModifiedCount());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260903_02 rollback – revoking {} from account managers", ACTION);
		db.getCollection(COLLECTION).updateMany(
			new Document("authorities.role", ROLE),
			new Document("$pull", new Document("authorities",
				new Document("action", ACTION).append("role", ROLE))));
	}
}
