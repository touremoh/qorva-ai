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
 * Grants CONTACT_CANDIDATE (email a candidate from the CV list / matching report) to accounts
 * created before candidate outreach existed.
 *
 * <p>Same mechanics as V20260903_01: authorities are written once from UserAuthoritiesHelper,
 * so a new action only reaches accounts created afterwards. Full accounts are recognised by
 * ADD_CV, which demo users never hold — their read-only set stays untouched. Account managers
 * carry ACCOUNT_MANAGER on their own authorities; they get the same grant under their role so
 * an owner can still revoke it per user from the Users tab.</p>
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260919_01__BackfillContactCandidateAuthority", order = "20260919_01", author = "qorva")
public class V2026091901BackfillContactCandidateAuthority extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "users";
	private static final String ACTION = "CONTACT_CANDIDATE";
	private static final String OWNER_ROLE = "ACCOUNT_OWNER";
	private static final String MANAGER_ROLE = "ACCOUNT_MANAGER";

	/** Held by full accounts and never by demo users, whose set is read-only by design. */
	private static final String FULL_ACCOUNT_MARKER = "ADD_CV";
	private static final String DEMO_STATUS = "DEMO";

	@Execution
	public void execute(MongoDatabase db) {
		var owners = db.getCollection(COLLECTION).updateMany(
			new Document("$and", List.of(
				new Document("authorities.action", FULL_ACCOUNT_MARKER),
				new Document("authorities.role", OWNER_ROLE),
				new Document("authorities.action", new Document("$ne", ACTION)),
				new Document("userAccountStatus", new Document("$ne", DEMO_STATUS)))),
			new Document("$push", new Document("authorities", authority(OWNER_ROLE))));

		var managers = db.getCollection(COLLECTION).updateMany(
			new Document("$and", List.of(
				new Document("authorities.role", MANAGER_ROLE),
				new Document("authorities.action", new Document("$ne", ACTION)),
				new Document("userAccountStatus", new Document("$ne", DEMO_STATUS)))),
			new Document("$push", new Document("authorities", authority(MANAGER_ROLE))));

		log.info("V20260919_01 – granted {} to {} owners and {} account managers",
			ACTION, owners.getModifiedCount(), managers.getModifiedCount());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260919_01 rollback – revoking {} from all users", ACTION);
		db.getCollection(COLLECTION).updateMany(
			new Document(),
			new Document("$pull", new Document("authorities", new Document("action", ACTION))));
	}

	private static Document authority(String role) {
		return new Document("role", role)
			.append("action", ACTION)
			.append("permission", "ALLOWED");
	}
}
