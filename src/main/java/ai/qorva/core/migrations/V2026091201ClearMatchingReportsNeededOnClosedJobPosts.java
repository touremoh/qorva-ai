package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * Clears matchingReportsNeeded on every closed job post. The ATS sync used to close a job by
 * writing its status alone, leaving the flag as it was. The screening run only ever clears
 * the flag on open jobs, so such a job was never cleared, and the app — which counted every
 * flagged job as pending — kept polling until its timeout on each matching run and showed
 * the "jobs need matching" banner for good.
 *
 * <p>The sync now derives the flag from the status transition
 * ({@code JobPostService.matchingReportsNeededFor}); this repairs the rows written before
 * it did.</p>
 */
@Slf4j
@Component
@ChangeUnit(id = "V20260912_01__ClearMatchingReportsNeededOnClosedJobPosts", order = "20260912_01", author = "qorva")
public class V2026091201ClearMatchingReportsNeededOnClosedJobPosts extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "job_posts";
	private static final String CLOSED = "closed";

	@Execution
	public void execute(MongoDatabase db) {
		var result = db.getCollection(COLLECTION).updateMany(
			new Document("status", CLOSED).append("matchingReportsNeeded", true),
			new Document("$set", new Document("matchingReportsNeeded", false)));

		log.info("V20260912_01 – cleared matchingReportsNeeded on {} closed job posts", result.getModifiedCount());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		// The flag is derived from the status; a closed job never needs screening, so there is
		// nothing meaningful to restore.
		log.warn("V20260912_01 rollback – nothing to restore, closed job posts stay unflagged");
	}
}
