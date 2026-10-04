package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adds the per-job matching state to the job_posts validator: why results are stale, the last run's Top N,
 * time and cutoff, and the new candidates seen since. All optional — absent means "never matched".
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261003_01__UpdateJobPostsCollectionMatching", order = "20261003_01", author = "qorva")
public class V2026100301UpdateJobPostsCollectionMatching extends AbstractQorvaDbMigration {

	private static final String DDL_FILE = "20261003_01__update_job_posts_collection.json";
	private static final String PREVIOUS_DDL_FILE = "20260901_04__update_job_posts_collection.json";

	@Execution
	public void execute(MongoDatabase db) {
		updateCollection(db, DDL_FILE, "V20261003_01 – job_posts schema validator: matching state");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		updateCollection(db, PREVIOUS_DDL_FILE, "V20261003_01 rollback – restoring the previous job_posts schema validator");
	}
}
