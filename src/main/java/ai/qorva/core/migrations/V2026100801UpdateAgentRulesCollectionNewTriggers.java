package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Lets agent_rules hold the new triggers (REPORT_STATUS_IDLE, CV_OUTDATED, JOB_CLOSED, DUPLICATE_FOUND,
 * CANDIDATE_PROFILE_UPDATED), CV_SCORED's verdicts and maximum score, and the profile-update pre-approval. Existing
 * rules need nothing: every new field is optional.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261008_01__UpdateAgentRulesCollectionNewTriggers", order = "20261008_01", author = "qorva")
public class V2026100801UpdateAgentRulesCollectionNewTriggers extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261008_01__update_agent_rules_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20261004_02__update_agent_rules_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261008_01 – agent_rules schema validator: new triggers");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261008_01 rollback – restoring the previous agent_rules schema validator");
	}
}
