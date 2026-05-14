package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChangeUnit(id = "V20260514_10__AddPerformanceIndexes", order = "20260514_10", author = "qorva")
public class V2026051410AddPerformanceIndexes extends AbstractQorvaDbMigration {

	private static final String TENANT_ID = "tenantId";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V20260514_10 – creating performance indexes");

		var cvs = db.getCollection("cvs");
		cvs.createIndex(Indexes.compoundIndex(Indexes.ascending(TENANT_ID), Indexes.ascending("tags")));
		cvs.createIndex(Indexes.compoundIndex(Indexes.ascending(TENANT_ID), Indexes.descending("score")));
		log.info("V20260514_10 – cvs indexes created");

		var matchingReports = db.getCollection("matching_reports");
		matchingReports.createIndex(
			Indexes.compoundIndex(
				Indexes.ascending(TENANT_ID),
				Indexes.ascending("jobPostId"),
				Indexes.ascending("candidateInfo.candidateId")
			),
			new IndexOptions().unique(true).sparse(true).name("unique_tenant_job_candidate")
		);
		log.info("V20260514_10 – matching_reports unique index created");

		var jobPosts = db.getCollection("job_posts");
		jobPosts.createIndex(Indexes.compoundIndex(
			Indexes.ascending(TENANT_ID),
			Indexes.ascending("status"),
			Indexes.descending("lastUpdatedAt")
		));
		log.info("V20260514_10 – job_posts indexes created");

		var users = db.getCollection("users");
		users.createIndex(
			Indexes.compoundIndex(Indexes.ascending(TENANT_ID), Indexes.ascending("email")),
			new IndexOptions().unique(true).name("unique_tenant_email")
		);
		log.info("V20260514_10 – users unique tenant+email index created");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260514_10 rollback – dropping performance indexes");
		try { db.getCollection("cvs").dropIndex("tenantId_1_tags_1"); } catch (Exception ignored) {}
		try { db.getCollection("cvs").dropIndex("tenantId_1_score_-1"); } catch (Exception ignored) {}
		try { db.getCollection("matching_reports").dropIndex("unique_tenant_job_candidate"); } catch (Exception ignored) {}
		try { db.getCollection("job_posts").dropIndex("tenantId_1_status_1_lastUpdatedAt_-1"); } catch (Exception ignored) {}
		try { db.getCollection("users").dropIndex("unique_tenant_email"); } catch (Exception ignored) {}
	}
}
