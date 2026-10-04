package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Indexes for the pipeline board's columns (all jobs or one job): New by best score, every other status by its
 * most recent move. Each column is a keyset page on one of these, whatever the size of the pipeline.
 */
@Slf4j
@Component
@ChangeUnit(id = "V20261005_01__AddPipelineBoardIndexes", order = "20261005_01", author = "qorva")
public class V2026100501AddPipelineBoardIndexes extends AbstractQorvaDbMigration {

	private static final String SCORE = "matchingReportDetails.decisionSummary.finalScore";

	record Index(String name, Document keys) {
	}

	static final List<Index> INDEXES = List.of(
		new Index("pipeline_status_changed_idx", new Document("tenantId", 1).append("status", 1).append("statusChangedAt", -1).append("_id", -1)),
		new Index("pipeline_job_status_changed_idx", new Document("tenantId", 1).append("jobPostId", 1).append("status", 1)
			.append("statusChangedAt", -1).append("_id", -1)),
		new Index("pipeline_status_score_idx", new Document("tenantId", 1).append("status", 1).append(SCORE, -1).append("_id", -1)),
		new Index("pipeline_job_status_score_idx", new Document("tenantId", 1).append("jobPostId", 1).append("status", 1)
			.append(SCORE, -1).append("_id", -1)));

	@Execution
	public void execute(MongoDatabase db) {
		var reports = db.getCollection("matching_reports");
		INDEXES.forEach(index -> reports.createIndex(index.keys(), new IndexOptions().name(index.name())));
		log.info("V20261005_01 – {} pipeline board indexes on matching_reports", INDEXES.size());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		var reports = db.getCollection("matching_reports");
		for (var index : INDEXES) {
			try {
				reports.dropIndex(index.name());
			} catch (Exception e) {
				log.warn("V20261005_01 rollback – index {} not dropped: {}", index.name(), e.getMessage());
			}
		}
	}
}
