package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * Copilot standing rules and their firing ledger, plus the indexes the rule triggers read through:
 * new CVs, re-scored reports and finished ATS syncs, each by tenant and timestamp.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261002_01__CreateAgentRulesCollections", order = "20261002_01", author = "qorva")
public class V2026100201CreateAgentRulesCollections extends AbstractQorvaDbMigration {

	private static final String RULES = "agent_rules";
	private static final String FIRINGS = "agent_rule_firings";
	private static final String DDL_FILE = "20261002_01__create_agent_rules_collection.json";

	private static final String RUNS_INDEX = "tenant_rule_status_idx";
	private static final String CVS_INDEX = "tenant_created_at_idx";
	private static final String REPORTS_INDEX = "tenant_last_updated_at_idx";
	private static final String JOBS_INDEX = "tenant_type_finished_idx";

	@Execution
	public void execute(MongoDatabase db) {
		createCollection(db, RULES, "V20261002_01 – creating agent_rules collection", DDL_FILE);
		var rules = db.getCollection(RULES);
		// Scheduler claim: active (or self-resuming) rules due a check whose lease is free.
		rules.createIndex(new Document("status", 1).append("nextCheckAt", 1),
			new IndexOptions().name("status_next_check_idx"));
		rules.createIndex(new Document("tenantId", 1).append("ownerEmail", 1),
			new IndexOptions().name("tenant_owner_idx"));

		createCollectionIfAbsent(db, FIRINGS);
		var firings = db.getCollection(FIRINGS);
		firings.createIndex(new Document("ruleId", 1).append("subjectKey", 1),
			new IndexOptions().name("rule_subject_uniq").unique(true));
		firings.createIndex(new Document("tenantId", 1),
			new IndexOptions().name("tenant_idx"));

		// A rule's runs: the one-run-at-a-time check and Activity's rule filter.
		db.getCollection("agent_runs").createIndex(new Document("tenantId", 1).append("ruleId", 1).append("status", 1),
			new IndexOptions().name(RUNS_INDEX));
		db.getCollection("cvs").createIndex(new Document("tenantId", 1).append("createdAt", 1),
			new IndexOptions().name(CVS_INDEX));
		db.getCollection("matching_reports").createIndex(new Document("tenantId", 1).append("lastUpdatedAt", 1),
			new IndexOptions().name(REPORTS_INDEX));
		db.getCollection("background_jobs").createIndex(new Document("tenantId", 1).append("type", 1).append("finishedAt", 1),
			new IndexOptions().name(JOBS_INDEX));
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20261002_01 rollback – dropping {} and {}, and the trigger indexes", RULES, FIRINGS);
		dropCollection(db, RULES);
		dropCollection(db, FIRINGS);
		db.getCollection("agent_runs").dropIndex(RUNS_INDEX);
		db.getCollection("cvs").dropIndex(CVS_INDEX);
		db.getCollection("matching_reports").dropIndex(REPORTS_INDEX);
		db.getCollection("background_jobs").dropIndex(JOBS_INDEX);
	}
}
