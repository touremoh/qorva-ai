package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Lets agent_rules hold the JOB_NEEDS_MATCHING trigger (with its stale reasons) and the owner's pre-approval of
 * matching. Existing rules need nothing: absent fields mean "all reasons" and "not pre-approved".
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261003_06__UpdateAgentRulesCollectionJobNeedsMatching", order = "20261003_06", author = "qorva")
public class V2026100306UpdateAgentRulesCollectionJobNeedsMatching extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261003_06__update_agent_rules_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20261002_01__create_agent_rules_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261003_06 – agent_rules schema validator: JOB_NEEDS_MATCHING and matching pre-approval");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261003_06 rollback – restoring the previous agent_rules schema validator");
	}
}
