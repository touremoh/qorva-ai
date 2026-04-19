package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adds compound indexes for:
 * - CV:           {tenantId, clientId}, {tenantId, tags}, {tenantId, score}
 * - ResumeMatch:  unique {tenantId, jobPostId, candidateInfo.candidateId}
 * - JobPost:      {tenantId, status, lastUpdatedAt}
 * - User:         unique {tenantId, email}
 */
@Slf4j
@Component
@ChangeUnit(id = "V011AddPerformanceIndexesChangeLog", order = "011", author = "qorva")
public class V011AddPerformanceIndexesChangeLog extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		log.info("Creating performance indexes");

		// CV indexes
		var cvs = db.getCollection("CVs");
		cvs.createIndex(Indexes.compoundIndex(Indexes.ascending("tenantId"), Indexes.ascending("clientId")));
		cvs.createIndex(Indexes.compoundIndex(Indexes.ascending("tenantId"), Indexes.ascending("tags")));
		cvs.createIndex(Indexes.compoundIndex(Indexes.ascending("tenantId"), Indexes.descending("score")));
		log.info("CV indexes created");

		// ResumeMatch: unique constraint to prevent duplicate reports per candidate per job per tenant
		var resumeMatches = db.getCollection("ResumeMatches");
		resumeMatches.createIndex(
			Indexes.compoundIndex(
				Indexes.ascending("tenantId"),
				Indexes.ascending("jobPostId"),
				Indexes.ascending("candidateInfo.candidateId")
			),
			new IndexOptions().unique(true).sparse(true).name("unique_tenant_job_candidate")
		);
		log.info("ResumeMatch unique index created");

		// JobPost indexes
		var jobPosts = db.getCollection("JobPosts");
		jobPosts.createIndex(Indexes.compoundIndex(
			Indexes.ascending("tenantId"),
			Indexes.ascending("status"),
			Indexes.descending("lastUpdatedAt")
		));
		log.info("JobPost indexes created");

		// User: unique email per tenant
		var users = db.getCollection("Users");
		users.createIndex(
			Indexes.compoundIndex(Indexes.ascending("tenantId"), Indexes.ascending("email")),
			new IndexOptions().unique(true).name("unique_tenant_email")
		);
		log.info("User unique tenant+email index created");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("Rolling back V011 – dropping performance indexes");
		try { db.getCollection("CVs").dropIndex("tenantId_1_clientId_1"); } catch (Exception ignored) {}
		try { db.getCollection("CVs").dropIndex("tenantId_1_tags_1"); } catch (Exception ignored) {}
		try { db.getCollection("CVs").dropIndex("tenantId_1_score_-1"); } catch (Exception ignored) {}
		try { db.getCollection("ResumeMatches").dropIndex("unique_tenant_job_candidate"); } catch (Exception ignored) {}
		try { db.getCollection("JobPosts").dropIndex("tenantId_1_status_1_lastUpdatedAt_-1"); } catch (Exception ignored) {}
		try { db.getCollection("Users").dropIndex("unique_tenant_email"); } catch (Exception ignored) {}
	}
}
