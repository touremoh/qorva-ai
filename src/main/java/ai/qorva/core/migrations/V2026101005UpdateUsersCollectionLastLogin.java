package ai.qorva.core.migrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Users record their last sign-in ({@code lastLoginAt}); absent until the next one. */
@Slf4j
@Component
@ChangeUnit(id = "V20261010_05__UpdateUsersCollectionLastLogin", order = "20261010_05", author = "qorva")
public class V2026101005UpdateUsersCollectionLastLogin extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, "20261010_05__update_users_collection_last_login.json", "V20261010_05 – users schema validator: lastLoginAt");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, "20261005_04__update_users_collection_invite.json", "V20261010_05 rollback – restoring the previous users validator");
	}
}
