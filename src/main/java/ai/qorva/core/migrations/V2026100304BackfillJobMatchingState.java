package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gives existing jobs the matching state the new flow reads: a job that already has reports gets
 * {@code lastMatchedAt} (its newest report), so new candidates are compared with it; an open job that never
 * had any is flagged {@code NEVER_RUN}. Idempotent — only fills fields that are absent.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261003_04__BackfillJobMatchingState", order = "20261003_04", author = "qorva")
public class V2026100304BackfillJobMatchingState extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		var jobs = db.getCollection("job_posts");
		long matched = 0;
		for (var row : db.getCollection("matching_reports").aggregate(List.of(
			new Document("$group", new Document("_id", "$jobPostId").append("last", new Document("$max", "$lastUpdatedAt")))))) {
			if (row.get("_id") == null || row.get("last") == null) continue;
			matched += jobs.updateOne(
				new Document("_id", row.get("_id")).append("lastMatchedAt", new Document("$exists", false)),
				new Document("$set", new Document("lastMatchedAt", row.get("last")))).getModifiedCount();
		}
		var neverRun = jobs.updateMany(
			new Document("status", "open")
				.append("lastMatchedAt", new Document("$exists", false))
				.append("matchingStaleReason", new Document("$exists", false)),
			new Document("$set", new Document("matchingStaleReason", "NEVER_RUN").append("matchingReportsNeeded", true)));
		log.info("V20261003_04 – lastMatchedAt set on {} job posts, {} open job posts flagged NEVER_RUN",
			matched, neverRun.getModifiedCount());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		// Optional fields the previous code never reads; leaving them is harmless.
		log.warn("V20261003_04 rollback – nothing to undo, the matching state fields are ignored by older code");
	}
}
