package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Re-runs the VIEW_LIBRARY_INSIGHTS grant of {@link V2026092601BackfillViewLibraryInsightsAuthority}.
 *
 * <p>Until the app's permission editor learned the action, saving a user's permissions (or inviting
 * one) replaced their authority list without it, silently revoking Talent Intelligence. This restores
 * it for every VIEW_CV holder who lost it since the first backfill. Idempotent.</p>
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260927_01__RegrantViewLibraryInsightsAuthority", order = "20260927_01", author = "qorva")
public class V2026092701RegrantViewLibraryInsightsAuthority extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		var granted = V2026092601BackfillViewLibraryInsightsAuthority.grantToViewCvHolders(db);
		log.info("V20260927_01 – granted VIEW_LIBRARY_INSIGHTS to {} users", granted);
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260927_01 rollback – nothing to undo automatically: the grant restores access the user should have had");
	}
}
