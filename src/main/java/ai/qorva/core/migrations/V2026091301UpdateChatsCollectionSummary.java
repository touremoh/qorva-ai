package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Declares chats.summary in the validator: the rolling LLM-written summary of the turns the
 * resume chat no longer sends verbatim (see ChatSummaryService). No data backfill — existing
 * chats start without a summary and get one on their next turn past the compaction threshold.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260913_01__UpdateChatsCollectionSummary", order = "20260913_01", author = "qorva")
public class V2026091301UpdateChatsCollectionSummary extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20260913_01__update_chats_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260913_01 – adding summary to the chats schema");
		updateCollection(db, DDL_FILE, "V20260913_01 – chats schema validator updated");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260913_01 rollback – restoring the previous chats validator");
		updateCollection(db, "20260514_06__create_chats_collection.json",
			"V20260913_01 rollback – chats validator restored to V20260514_06");
	}
}
