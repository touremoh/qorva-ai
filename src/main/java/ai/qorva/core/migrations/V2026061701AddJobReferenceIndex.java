package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChangeUnit(id = "V20260617_01__AddJobReferenceIndex", order = "20260617_01", author = "qorva")
public class V2026061701AddJobReferenceIndex extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		var jobPosts = db.getCollection("job_posts");

		jobPosts.createIndex(
			Indexes.compoundIndex(Indexes.ascending("tenantId"), Indexes.ascending("jobReference")),
			new IndexOptions().unique(true).sparse(true).name("job_posts_job_reference_idx")
		);
		log.info("V20260617_01 – job_posts_job_reference_idx created");

		try {
			jobPosts.dropIndex("job_posts_text_search_idx");
			log.info("V20260617_01 – dropped existing job_posts_text_search_idx");
		} catch (Exception e) {
			log.warn("V20260617_01 – job_posts_text_search_idx not found, skipping drop: {}", e.getMessage());
		}

		jobPosts.createIndex(
			new Document("tenantId", 1)
				.append("jobReference", "text")
				.append("title", "text")
				.append("description", "text"),
			new IndexOptions().name("job_posts_text_search_idx")
		);
		log.info("V20260617_01 – job_posts_text_search_idx recreated with jobReference");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260617_01 rollback – dropping job reference indexes");
		try { db.getCollection("job_posts").dropIndex("job_posts_job_reference_idx"); } catch (Exception ignored) {}
		try { db.getCollection("job_posts").dropIndex("job_posts_text_search_idx"); } catch (Exception ignored) {}
	}
}
