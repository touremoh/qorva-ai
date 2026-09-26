package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Grants VIEW_LIBRARY_INSIGHTS to every user who can view CVs but was created before the action
 * existed (2026-05-31).
 *
 * <p>Talent Intelligence ran without any authority check until the {@code @PreAuthorize} on
 * {@code LibraryInsightsController} was restored, so anyone who could read the library could use it.
 * Holding VIEW_CV is that same population. The grant goes under the role the user's VIEW_CV sits
 * under, so an owner can still revoke it per user from the Users tab. Idempotent: users that
 * already hold the action are not matched.</p>
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260926_01__BackfillViewLibraryInsightsAuthority", order = "20260926_01", author = "qorva")
public class V2026092601BackfillViewLibraryInsightsAuthority extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "users";
	private static final String ACTION = "VIEW_LIBRARY_INSIGHTS";
	private static final String VIEW_CV = "VIEW_CV";
	private static final String DEFAULT_ROLE = "ACCOUNT_OWNER";

	@Execution
	public void execute(MongoDatabase db) {
		var users = db.getCollection(COLLECTION);
		var granted = 0L;
		var candidates = users.find(Filters.and(
			Filters.eq("authorities.action", VIEW_CV),
			Filters.ne("authorities.action", ACTION)));

		for (Document user : candidates) {
			var result = users.updateOne(
				Filters.and(Filters.eq("_id", user.get("_id")), Filters.ne("authorities.action", ACTION)),
				Updates.push("authorities", authority(roleOfViewCv(user))));
			granted += result.getModifiedCount();
		}

		log.info("V20260926_01 – granted {} to {} users", ACTION, granted);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260926_01 rollback – nothing to undo automatically: {} may have been granted on purpose since", ACTION);
	}

	private static String roleOfViewCv(Document user) {
		List<Document> authorities = user.getList("authorities", Document.class, List.of());
		return authorities.stream()
			.filter(a -> VIEW_CV.equals(a.getString("action")))
			.map(a -> a.getString("role"))
			.filter(role -> role != null && !role.isBlank())
			.findFirst()
			.orElse(DEFAULT_ROLE);
	}

	private static Document authority(String role) {
		return new Document("role", role)
			.append("action", ACTION)
			.append("permission", "ALLOWED");
	}
}
