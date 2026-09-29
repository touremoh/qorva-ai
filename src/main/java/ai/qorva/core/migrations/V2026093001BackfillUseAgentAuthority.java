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
 * Grants USE_AGENT (Copilot) to existing account owners and account managers. New owners get it
 * from UserAuthoritiesHelper; new managers from the app's invite form, which starts from every
 * permission. Roles are read from the user's own authorities (the user document has no role field).
 *
 * <p>Demo accounts are excluded: their authority set is deliberately read-only.</p>
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260930_01__BackfillUseAgentAuthority", order = "20260930_01", author = "qorva")
public class V2026093001BackfillUseAgentAuthority extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "users";
	private static final String ACTION = "USE_AGENT";
	private static final List<String> ROLES = List.of("ACCOUNT_OWNER", "ACCOUNT_MANAGER");
	private static final String DEMO_STATUS = "DEMO";

	@Execution
	public void execute(MongoDatabase db) {
		for (var role : ROLES) {
			var authority = new Document("role", role)
				.append("action", ACTION)
				.append("permission", "ALLOWED");

			var target = new Document("$and", List.of(
				new Document("authorities.role", role),
				new Document("authorities.action", new Document("$ne", ACTION)),
				new Document("userAccountStatus", new Document("$ne", DEMO_STATUS))));

			var result = db.getCollection(COLLECTION)
				.updateMany(target, new Document("$push", new Document("authorities", authority)));

			log.info("V20260930_01 – granted {} to {} users with role {}", ACTION, result.getModifiedCount(), role);
		}
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260930_01 rollback – revoking {}", ACTION);
		db.getCollection(COLLECTION).updateMany(
			new Document("authorities.action", ACTION),
			new Document("$pull", new Document("authorities", new Document("action", ACTION))));
	}
}
