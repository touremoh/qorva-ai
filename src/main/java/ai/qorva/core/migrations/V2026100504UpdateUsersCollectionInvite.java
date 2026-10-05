package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Lets users hold the invite state ({@code invitePending}, {@code invitedAt}, {@code invitedBy}). No backfill
 * (decision 2026-10-05): users invited before invites were links keep no flag and use "Forgot password?".
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261005_04__UpdateUsersCollectionInvite", order = "20261005_04", author = "qorva")
public class V2026100504UpdateUsersCollectionInvite extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261005_04__update_users_collection_invite.json";
	private static final String PREVIOUS_DDL_FILE = "20260924_02__update_users_collection_mfa.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261005_04 – users schema validator: invite state");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261005_04 rollback – restoring the previous users schema validator");
	}
}
