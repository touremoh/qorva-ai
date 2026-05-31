package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dto.ClusterBucket;
import ai.qorva.core.dto.ExtractedFilters;
import ai.qorva.core.dto.SkillFrequencyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CVInsightRepositoryImpl implements CVInsightRepository {

	private static final Set<String> ALLOWED_CLUSTER_DIMENSIONS = Set.of(
		"seniorityLevel", "skillDepth", "leadershipAndInfluence", "learningVelocity", "primaryCluster"
	);

	private final MongoTemplate mongoTemplate;

	@Override
	public long countCandidatesByFilters(ObjectId tenantId, ExtractedFilters filters) {
		return mongoTemplate.count(Query.query(buildCriteria(tenantId, filters)), CV.class);
	}

	@Override
	public List<SkillFrequencyResult> getSkillFrequencyReport(ObjectId tenantId, ExtractedFilters filters, int limit) {
		AggregationOperation matchOp = Aggregation.match(buildCriteria(tenantId, filters));
		AggregationOperation unwindCategories = Aggregation.unwind("keySkills");
		AggregationOperation unwindSkills = Aggregation.unwind("keySkills.skills");
		AggregationOperation groupOp = ctx -> new Document("$group",
			new Document("_id", "$keySkills.skills")
				.append("count", new Document("$sum", 1)));
		AggregationOperation projectOp = ctx -> new Document("$project",
			new Document("skill", "$_id")
				.append("count", 1)
				.append("_id", 0));
		AggregationOperation sortOp = Aggregation.sort(Sort.Direction.DESC, "count");
		AggregationOperation limitOp = Aggregation.limit(limit);

		return mongoTemplate.aggregate(
			Aggregation.newAggregation(matchOp, unwindCategories, unwindSkills, groupOp, projectOp, sortOp, limitOp),
			CV.class,
			SkillFrequencyResult.class
		).getMappedResults();
	}

	@Override
	public List<ClusterBucket> getClusterDistributionReport(ObjectId tenantId, String clusterDimension) {
		if (!ALLOWED_CLUSTER_DIMENSIONS.contains(clusterDimension)) {
			throw new IllegalArgumentException("Unknown cluster dimension: " + clusterDimension);
		}

		String fieldPath = "candidateClustering." + clusterDimension;

		AggregationOperation matchOp = Aggregation.match(
			Criteria.where("tenantId").is(tenantId)
				.and(fieldPath).exists(true).ne(null)
		);
		AggregationOperation groupOp = ctx -> new Document("$group",
			new Document("_id", "$" + fieldPath)
				.append("count", new Document("$sum", 1)));
		AggregationOperation projectOp = ctx -> new Document("$project",
			new Document("name", "$_id")
				.append("count", 1)
				.append("_id", 0));
		AggregationOperation sortOp = Aggregation.sort(Sort.Direction.DESC, "count");

		return mongoTemplate.aggregate(
			Aggregation.newAggregation(matchOp, groupOp, projectOp, sortOp),
			CV.class,
			ClusterBucket.class
		).getMappedResults();
	}

	// Stop-words that are too generic to use as role keywords in isolation
	private static final Set<String> ROLE_STOP_WORDS = Set.of(
		"developer", "engineer", "specialist", "consultant", "manager", "architect",
		"lead", "senior", "junior", "mid", "staff", "principal"
	);

	/**
	 * Extracts meaningful keyword tokens from a role string for partial regex matching.
	 * "Spring Boot Developer" → ["Spring Boot", "Developer"] but we keep the full phrase
	 * as the first option so specific matches rank first, then fall back to keyword-level.
	 * Generic titles like just "Developer" are excluded to avoid over-matching.
	 */
	private List<String> extractKeywords(String role) {
		// Always include the full phrase as one option (regex partial match)
		List<String> keywords = new java.util.ArrayList<>();
		keywords.add(role);

		// Also add individual meaningful words (skip stop-words when alone, but include tech terms)
		String[] words = role.split("\\s+");
		for (String word : words) {
			String lower = word.toLowerCase();
			// Include word only if it's a technology/domain term (not a generic title)
			if (word.length() > 3 && !ROLE_STOP_WORDS.contains(lower)) {
				keywords.add(word);
			}
		}
		return keywords;
	}

	private Criteria buildCriteria(ObjectId tenantId, ExtractedFilters filters) {
		// Collect all conditions into a list — avoids the "can't add a second $and" limitation
		List<Criteria> conditions = new ArrayList<>();
		conditions.add(Criteria.where("tenantId").is(tenantId));

		if (filters == null) {
			return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
		}

		if (filters.skills() != null && !filters.skills().isEmpty()) {
			List<Criteria> skillOr = filters.skills().stream()
				.map(skill -> Criteria.where("keySkills.skills").regex("\\b" + skill + "\\b", "i"))
				.collect(Collectors.toList());
			conditions.add(new Criteria().orOperator(skillOr.toArray(new Criteria[0])));
		}

		if (filters.roles() != null && !filters.roles().isEmpty()) {
			List<Criteria> roleOr = filters.roles().stream()
				.flatMap(role -> extractKeywords(role).stream()
					.map(kw -> Criteria.where("personalInformation.role").regex(kw, "i")))
				.collect(Collectors.toList());
			if (!roleOr.isEmpty()) {
				conditions.add(new Criteria().orOperator(roleOr.toArray(new Criteria[0])));
			}
		}

		if (filters.seniority() != null) {
			conditions.add(Criteria.where("candidateClustering.seniorityLevel").is(filters.seniority()));
		}
		if (filters.skillDepth() != null) {
			conditions.add(Criteria.where("candidateClustering.skillDepth").is(filters.skillDepth()));
		}
		if (filters.leadershipLevel() != null) {
			conditions.add(Criteria.where("candidateClustering.leadershipAndInfluence").is(filters.leadershipLevel()));
		}
		if (filters.location() != null) {
			conditions.add(Criteria.where("personalInformation.contact").regex(filters.location(), "i"));
		}
		if (filters.minYearsExperience() != null) {
			conditions.add(Criteria.where("careerStartYear").lte(Year.now().getValue() - filters.minYearsExperience()));
		}
		if (filters.tags() != null && !filters.tags().isEmpty()) {
			conditions.add(Criteria.where("tags").in(filters.tags()));
		}

		return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
	}
}
