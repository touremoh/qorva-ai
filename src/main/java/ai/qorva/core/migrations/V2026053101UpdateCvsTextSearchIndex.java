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
			Indexes.compoundIndex(
				Indexes.text("candidateProfileSummary"),
				Indexes.text("personalInformation.name"),
				Indexes.text("personalInformation.contact.email"),
				Indexes.text("personalInformation.role"),
				Indexes.text("tags"),
				Indexes.text("keySkills.category"),
				Indexes.text("keySkills.skills"),
				Indexes.text("skillsAndQualifications.softSkills"),
				Indexes.text("skillsAndQualifications.technicalSkills"),
				Indexes.text("profiles.areasOfExpertise"),
				Indexes.text("applicantNumber"),
				Indexes.text("candidateClustering.primaryCluster"),
				Indexes.text("candidateClustering.secondaryClusters"),
				Indexes.text("candidateClustering.functionalExpertise"),
				Indexes.text("candidateClustering.industryDomains"),
				Indexes.text("candidateClustering.environmentFit"),
				Indexes.text("candidateClustering.clusterReasoning"),
				Indexes.text("candidateClustering.seniorityLevel"),
				Indexes.text("candidateClustering.leadershipAndInfluence")
			),
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
