package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Re-applies the ats_connections validator with Ashby in the provider list. The schema is
 * unchanged — provider is a free string — but the validator is where the supported set is
 * documented, and a stale list there is what someone reads first when debugging a
 * connection.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260906_01__UpdateAtsConnectionsProviders", order = "20260906_01", author = "qorva")
public class V2026090601UpdateAtsConnectionsProviders extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260906_01__update_ats_connections_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20260906_01 – ats_connections validator lists Ashby");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260906_01 rollback – restoring the V20260901_01 ats_connections validator");
		updateCollection(db, "20260901_01__create_ats_connections_collection.json",
			"V20260906_01 rollback – ats_connections validator restored");
	}
}
