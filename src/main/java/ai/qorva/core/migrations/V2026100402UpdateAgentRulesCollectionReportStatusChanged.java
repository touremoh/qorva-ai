package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Lets agent_rules hold the REPORT_STATUS_CHANGED trigger (with the statuses it watches). Existing rules need
 * nothing: an absent {@code toStatuses} means "any status".
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261004_02__UpdateAgentRulesCollectionReportStatusChanged", order = "20261004_02", author = "qorva")
public class V2026100402UpdateAgentRulesCollectionReportStatusChanged extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261004_02__update_agent_rules_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20261003_06__update_agent_rules_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261004_02 – agent_rules schema validator: REPORT_STATUS_CHANGED");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261004_02 rollback – restoring the previous agent_rules schema validator");
	}
}
