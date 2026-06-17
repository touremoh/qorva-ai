package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChangeUnit(id = "V20260531_01__UpdateCvsTextSearchIndex", order = "20260531_01", author = "qorva")
public class V2026053101UpdateCvsTextSearchIndex extends AbstractQorvaDbMigration {

	@Execution
	public void execute(MongoDatabase db) {
		var cvs = db.getCollection("cvs");

		try {
			cvs.dropIndex("cvs_text_search_idx");
			log.info("V20260531_01 – dropped existing cvs_text_search_idx");
		} catch (Exception e) {
			log.warn("V20260531_01 – cvs_text_search_idx not found, skipping drop: {}", e.getMessage());
		}

		cvs.createIndex(
			new Document("tenantId", 1)
				.append("candidateProfileSummary", "text")
				.append("personalInformation.name", "text")
				.append("personalInformation.contact.email", "text")
				.append("personalInformation.role", "text")
				.append("tags", "text")
				.append("keySkills.category", "text")
				.append("keySkills.skills", "text")
				.append("skillsAndQualifications.softSkills", "text")
				.append("skillsAndQualifications.technicalSkills", "text")
				.append("profiles.areasOfExpertise", "text")
				.append("applicantNumber", "text")
				.append("candidateClustering.primaryCluster", "text")
				.append("candidateClustering.secondaryClusters", "text")
				.append("candidateClustering.functionalExpertise", "text")
				.append("candidateClustering.industryDomains", "text")
				.append("candidateClustering.environmentFit", "text")
				.append("candidateClustering.clusterReasoning", "text")
				.append("candidateClustering.seniorityLevel", "text")
				.append("candidateClustering.leadershipAndInfluence", "text"),
			new IndexOptions().name("cvs_text_search_idx")
		);

		log.info("V20260531_01 – cvs_text_search_idx recreated with candidateClustering fields");
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V20260531_01 rollback – dropping updated cvs_text_search_idx");
		try { db.getCollection("cvs").dropIndex("cvs_text_search_idx"); } catch (Exception ignored) {}
	}
}
