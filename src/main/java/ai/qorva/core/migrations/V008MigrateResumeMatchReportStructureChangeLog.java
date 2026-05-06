package ai.qorva.core.migrations;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migrates aiAnalysisReportDetails in ResumeMatches from the old structure
 * (skillsMatch/experienceAlignment/exceedsRequirements/lackingSkills/overallSummary)
 * to the new structure
 * (skillsMatch/experienceMatch/locationMatch/industryMatch/missingSkills/finalScore).
 */
@Slf4j
@Component
@ChangeUnit(id = "V008MigrateResumeMatchReportStructureChangeLog", order = "008", author = "qorva")
public class V008MigrateResumeMatchReportStructureChangeLog extends AbstractQorvaDbMigration {

	private static final String COLLECTION = "ResumeMatches";

	@Execution
	public void execute(MongoDatabase db) {
		log.info("V008 – migrating ResumeMatches report structure");

		updateValidator(db);

		var collection = db.getCollection(COLLECTION);

		// Only migrate documents that still have the old structure
		var filter = Filters.exists("aiAnalysisReportDetails.overallSummary");

		var pipeline = List.of(
			new Document("$set", new Document()
				// skillsMatch: map degreeOfMatch → score, summary → scoreExplanation
				.append("aiAnalysisReportDetails.skillsMatch.score",
					new Document("$ifNull", List.of("$aiAnalysisReportDetails.skillsMatch.degreeOfMatch", 0)))
				.append("aiAnalysisReportDetails.skillsMatch.scoreExplanation",
					new Document("$ifNull", List.of("$aiAnalysisReportDetails.skillsMatch.summary", "")))
				.append("aiAnalysisReportDetails.skillsMatch.matchingSkills", List.of())

				// experienceMatch: from experienceAlignment
				.append("aiAnalysisReportDetails.experienceMatch", new Document()
					.append("score", new Document("$ifNull",
						List.of("$aiAnalysisReportDetails.experienceAlignment.degreeOfMatch", 0)))
					.append("scoreExplanation", new Document("$ifNull",
						List.of("$aiAnalysisReportDetails.experienceAlignment.summary", ""))))

				// locationMatch: no old data — default to 0
				.append("aiAnalysisReportDetails.locationMatch", new Document()
					.append("score", 0)
					.append("scoreExplanation", "Not available for legacy reports"))

				// industryMatch: no old data — default to 0
				.append("aiAnalysisReportDetails.industryMatch", new Document()
					.append("score", 0)
					.append("scoreExplanation", "Not available for legacy reports"))

				// missingSkills: from lackingSkills
				.append("aiAnalysisReportDetails.missingSkills", new Document()
					.append("summary", new Document("$ifNull",
						List.of("$aiAnalysisReportDetails.lackingSkills.summary", "")))
					.append("skills", new Document("$ifNull",
						List.of("$aiAnalysisReportDetails.lackingSkills.skills", List.of()))))

				// finalScore: from overallSummary
				.append("aiAnalysisReportDetails.finalScore", new Document()
					.append("score", new Document("$ifNull",
						List.of("$aiAnalysisReportDetails.overallSummary.score", 0)))
					.append("scoreExplanation", new Document("$ifNull",
						List.of("$aiAnalysisReportDetails.overallSummary.summary", ""))))
			),
			// Remove old fields
			new Document("$unset", List.of(
				"aiAnalysisReportDetails.skillsMatch.summary",
				"aiAnalysisReportDetails.skillsMatch.degreeOfMatch",
				"aiAnalysisReportDetails.experienceAlignment",
				"aiAnalysisReportDetails.exceedsRequirements",
				"aiAnalysisReportDetails.lackingSkills",
				"aiAnalysisReportDetails.overallSummary"
			))
		);

		var result = collection.updateMany(filter, pipeline);
		log.info("V008 – migrated {} ResumeMatch documents", result.getModifiedCount());
	}

	@RollbackExecution
	public void rollback(MongoDatabase db) {
		log.warn("V008 rollback – ResumeMatch report structure migration is not reversible (data was transformed)");
	}

	private void updateValidator(MongoDatabase db) {
		var collInfo = db.listCollections().filter(Filters.eq("name", COLLECTION)).first();
		if (collInfo == null) return;

		var options = collInfo.get("options", Document.class);
		if (options == null) return;

		var validator = options.get("validator", Document.class);
		if (validator == null) return;

		var jsonSchema = validator.get("$jsonSchema", Document.class);
		if (jsonSchema == null) return;

		var properties = jsonSchema.get("properties", Document.class);
		if (properties == null) return;

		var aiAnalysisSchema = properties.get("aiAnalysisReportDetails", Document.class);
		if (aiAnalysisSchema == null) return;

		// Replace required array with new field names
		aiAnalysisSchema.put("required", List.of(
			"skillsMatch", "experienceMatch", "locationMatch", "industryMatch", "missingSkills", "finalScore"
		));

		// Update properties: remove old, add new
		var aiProperties = aiAnalysisSchema.get("properties", Document.class);
		if (aiProperties != null) {
			aiProperties.remove("exceedsRequirements");
			aiProperties.remove("experienceAlignment");
			aiProperties.remove("overallSummary");
			aiProperties.remove("lackingSkills");
			aiProperties.put("experienceMatch", new Document("bsonType", "object"));
			aiProperties.put("locationMatch", new Document("bsonType", "object"));
			aiProperties.put("industryMatch", new Document("bsonType", "object"));
			aiProperties.put("missingSkills", new Document("bsonType", "object"));
			aiProperties.put("finalScore", new Document("bsonType", "object"));
		}

		db.runCommand(new Document("collMod", COLLECTION)
			.append("validator", validator)
			.append("validationAction", "error")
			.append("validationLevel", "strict")
		);
		log.info("V008 – ResumeMatches validator updated to new aiAnalysisReportDetails structure");
	}
}
