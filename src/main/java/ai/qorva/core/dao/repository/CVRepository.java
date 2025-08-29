package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dto.DashboardData;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CVRepository extends QorvaRepository<CV> {

	@Query(value = "{ '$text': { $search: ?0 }, 'tenantId': ?1 }")
	Page<CV> searchAll(String searchTerms, String tenantId, Pageable pageable);

	@Aggregation(pipeline = {
		"{ $vectorSearch: { " +
			"index: 'CVsSearchIndex', " +
			"queryVector: ?0, " +
			"path: 'embedding', " +
			"numCandidates: 800, " +     // increased recall
			"limit: 100, " +
			"filter: { tenantId: { $eq: ?1 } } " +
			"} }",
		"{ $addFields: { score: { $meta: 'vectorSearchScore' } } }",
		"{ $match: { score: { $gte: 0.4 } } }", // lowered threshold
	})
	List<CV> similaritySearch(float[] queryEmbedding, ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ $match: { tenantId: ?0 }}",
		"{ $unwind: '$tags' }",
		"{ $group: { _id: null, allTags: { $addToSet: '$tags' }}}",
		"{ $project: { _id: 0, tags: '$allTags' }}",
		"{ $sort: { tags: 1 } }"
	})
	List<String> findAllTagsByTenantId(ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0 } }",
		"{ '$unwind': '$keySkills' }",
		"{ '$unwind': '$keySkills.skills' }",
		"{ '$group': { '_id': '$keySkills.skills', 'totalMatch': { '$sum': 1 } } }",
		"{ '$project': { 'skill': '$_id', 'totalMatch': 1, '_id': 0 } }",
		"{ '$sort': { 'totalMatch': -1 } }"
	})
	List<DashboardData.SkillReport> getSkillReportByTenantId(ObjectId tenantId);

}
