package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.querybuilder.InsightCVQueryBuilder;
import ai.qorva.core.dto.CVQueryParams;
import ai.qorva.core.dto.ClusterBucket;
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

import java.util.List;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CVInsightRepositoryImpl implements CVInsightRepository {

	private static final Set<String> ALLOWED_CLUSTER_DIMENSIONS = Set.of(
		"seniorityLevel", "skillDepth", "leadershipAndInfluence", "learningVelocity", "primaryCluster"
	);

	private final MongoTemplate mongoTemplate;
	private final InsightCVQueryBuilder queryBuilder;

	@Override
	public long countCandidatesByFilters(ObjectId tenantId, CVQueryParams params) {
		return mongoTemplate.count(Query.query(queryBuilder.build(tenantId, params)), CV.class);
	}

	@Override
	public List<SkillFrequencyResult> getSkillFrequencyReport(ObjectId tenantId, CVQueryParams params, int limit) {
		AggregationOperation matchOp = Aggregation.match(queryBuilder.build(tenantId, params));
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
	public List<SkillFrequencyResult> getRareSkillsReport(ObjectId tenantId, CVQueryParams params, int maxCount, int limit) {
		AggregationOperation matchOp = Aggregation.match(queryBuilder.build(tenantId, params));
		AggregationOperation unwindCategories = Aggregation.unwind("keySkills");
		AggregationOperation unwindSkills = Aggregation.unwind("keySkills.skills");
		AggregationOperation groupOp = ctx -> new Document("$group",
			new Document("_id", "$keySkills.skills")
				.append("count", new Document("$sum", 1)));
		AggregationOperation filterRareOp = ctx -> new Document("$match",
			new Document("count", new Document("$lte", maxCount)));
		AggregationOperation projectOp = ctx -> new Document("$project",
			new Document("skill", "$_id")
				.append("count", 1)
				.append("_id", 0));
		AggregationOperation sortOp = Aggregation.sort(Sort.Direction.ASC, "count");
		AggregationOperation limitOp = Aggregation.limit(limit);

		return mongoTemplate.aggregate(
			Aggregation.newAggregation(matchOp, unwindCategories, unwindSkills, groupOp, filterRareOp, projectOp, sortOp, limitOp),
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
}
