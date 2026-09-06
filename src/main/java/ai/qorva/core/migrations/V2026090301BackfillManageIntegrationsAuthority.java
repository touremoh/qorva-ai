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
 * Grants MANAGE_INTEGRATIONS to accounts created before ATS integrations existed.
 *
 * <p>Authorities are stored on the user document, written once from UserAuthoritiesHelper
 * at checkout. Adding an action to that helper therefore only reaches accounts created
 * afterwards — every existing user keeps the list they were created with and gets 403 on
 * the ATS endpoints. Values are spelled out rather than read from the enums so this
 * migration keeps describing what was granted on this date even if the enums move.</p>
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260903_01__BackfillManageIntegrationsAuthority", order = "20260903_01", author = "qorva")
public class V2026090301BackfillManageIntegrationsAuthority extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "users";
	private static final String ACTION = "MANAGE_INTEGRATIONS";

	/** Held by full accounts and never by demo users, whose set is read-only by design. */
	private static final String FULL_ACCOUNT_MARKER = "ADD_CV";

	@Execution
	public void execute(MongoDatabase db) {
		var authority = new Document("role", "ACCOUNT_OWNER")
			.append("action", ACTION)
			.append("permission", "ALLOWED");

		var target = new Document("$and", List.of(
			new Document("authorities.action", FULL_ACCOUNT_MARKER),
			new Document("authorities.action", new Document("$ne", ACTION))));

		var result = db.getCollection(COLLECTION)
			.updateMany(target, new Document("$push", new Document("authorities", authority)));

		log.info("V20260903_01 – granted {} to {} existing users", ACTION, result.getModifiedCount());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260903_01 rollback – revoking {} from all users", ACTION);
		db.getCollection(COLLECTION).updateMany(
			new Document(),
			new Document("$pull", new Document("authorities", new Document("action", ACTION))));
	}
}
