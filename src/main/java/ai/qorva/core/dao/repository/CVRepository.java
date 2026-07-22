package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.dto.DashboardData;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;


public interface CVRepository extends QorvaRepository<CV>, SimilaritySearchRepository {

	@Query(value = "{ '$text': { $search: ?0 }, 'tenantId': ?1 }")
	Page<CV> searchAll(String searchTerms, String tenantId, Pageable pageable);

	/** Deletes every CV belonging to a tenant (used when purging demo data on upgrade). */
	long deleteByTenantId(String tenantId);

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
		"{ '$sort': { 'totalMatch': -1 } }",
		"{ '$limit': 10 }"
	})
	List<DashboardData.SkillReport> getSkillReportByTenantId(ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0, 'candidateClustering.skillDepth': { '$exists': true, '$ne': null } } }",
		"{ '$group': { '_id': '$candidateClustering.skillDepth', 'count': { '$sum': 1 } } }",
		"{ '$project': { 'name': '$_id', 'count': 1, 'percentage': { '$literal': 0.0 }, '_id': 0 } }",
		"{ '$sort': { 'count': -1 } }"
	})
	List<DashboardData.ClusteringCategoryReport> getSkillDepthReportByTenantId(ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0, 'candidateClustering.seniorityLevel': { '$exists': true, '$ne': null } } }",
		"{ '$group': { '_id': '$candidateClustering.seniorityLevel', 'count': { '$sum': 1 } } }",
		"{ '$project': { 'name': '$_id', 'count': 1, 'percentage': { '$literal': 0.0 }, '_id': 0 } }",
		"{ '$sort': { 'count': -1 } }"
	})
	List<DashboardData.ClusteringCategoryReport> getSeniorityLevelReportByTenantId(ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0, 'candidateClustering.leadershipAndInfluence': { '$exists': true, '$ne': null } } }",
		"{ '$group': { '_id': '$candidateClustering.leadershipAndInfluence', 'count': { '$sum': 1 } } }",
		"{ '$project': { 'name': '$_id', 'count': 1, 'percentage': { '$literal': 0.0 }, '_id': 0 } }",
		"{ '$sort': { 'count': -1 } }"
	})
	List<DashboardData.ClusteringCategoryReport> getLeadershipReportByTenantId(ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0, 'candidateClustering.learningVelocity': { '$exists': true, '$ne': null } } }",
		"{ '$group': { '_id': '$candidateClustering.learningVelocity', 'count': { '$sum': 1 } } }",
		"{ '$project': { 'name': '$_id', 'count': 1, 'percentage': { '$literal': 0.0 }, '_id': 0 } }",
		"{ '$sort': { 'count': -1 } }"
	})
	List<DashboardData.ClusteringCategoryReport> getLearningVelocityReportByTenantId(ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0, 'personalInformation.contact.email': { '$exists': true, '$ne': null, '$gt': '' } } }",
		"{ '$group': { '_id': '$personalInformation.contact.email', " +
			"'cvs': { '$push': { 'cvId': { '$toString': '$_id' }, 'name': '$personalInformation.name', " +
				"'email': '$personalInformation.contact.email', 'phone': '$personalInformation.contact.phone', 'createdAt': '$createdAt' } }, " +
			"'count': { '$sum': 1 } } }",
		"{ '$match': { 'count': { '$gt': 1 } } }",
		"{ '$project': { 'matchValue': '$_id', 'cvs': 1, 'count': 1, '_id': 0 } }",
		"{ '$sort': { 'count': -1 } }"
	})
	List<CVDuplicatesData.DuplicateAggResult> findEmailDuplicates(ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0, 'personalInformation.contact.phone': { '$exists': true, '$ne': null, '$gt': '' } } }",
		"{ '$group': { '_id': '$personalInformation.contact.phone', " +
			"'cvs': { '$push': { 'cvId': { '$toString': '$_id' }, 'name': '$personalInformation.name', " +
				"'email': '$personalInformation.contact.email', 'phone': '$personalInformation.contact.phone', 'createdAt': '$createdAt' } }, " +
			"'count': { '$sum': 1 } } }",
		"{ '$match': { 'count': { '$gt': 1 } } }",
		"{ '$project': { 'matchValue': '$_id', 'cvs': 1, 'count': 1, '_id': 0 } }",
		"{ '$sort': { 'count': -1 } }"
	})
	List<CVDuplicatesData.DuplicateAggResult> findPhoneDuplicates(ObjectId tenantId);

}
