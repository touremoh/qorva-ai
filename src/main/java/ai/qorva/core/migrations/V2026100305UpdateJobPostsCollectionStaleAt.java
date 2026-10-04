package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adds {@code matchingStaleAt} — when a job's matching results became out of date — to the job_posts validator.
 * No backfill: a rule watching for it starts from its creation, so episodes before that never fire.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261003_05__UpdateJobPostsCollectionStaleAt", order = "20261003_05", author = "qorva")
public class V2026100305UpdateJobPostsCollectionStaleAt extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261003_05__update_job_posts_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20261003_01__update_job_posts_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261003_05 – job_posts schema validator: matchingStaleAt");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261003_05 rollback – restoring the previous job_posts schema validator");
	}
}
