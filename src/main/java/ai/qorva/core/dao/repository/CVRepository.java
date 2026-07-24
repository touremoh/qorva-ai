package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.dto.LibraryQualityReport;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;


public interface CVRepository extends QorvaRepository<CV>, SimilaritySearchRepository, CVQualityRepository {

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

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0 } }",
		"{ '$group': { '_id': null, 'total': { '$sum': 1 }, " +
			"'hasName': { '$sum': { '$cond': [ { '$gt': [ '$personalInformation.name', '' ] }, 1, 0 ] } }, " +
			"'hasRole': { '$sum': { '$cond': [ { '$gt': [ '$personalInformation.role', '' ] }, 1, 0 ] } }, " +
			"'hasEmail': { '$sum': { '$cond': [ { '$gt': [ '$personalInformation.contact.email', '' ] }, 1, 0 ] } }, " +
			"'hasPhone': { '$sum': { '$cond': [ { '$gt': [ '$personalInformation.contact.phone', '' ] }, 1, 0 ] } }, " +
			"'missingContact': { '$sum': { '$cond': [ { '$and': [ " +
				"{ '$not': [ { '$gt': [ '$personalInformation.contact.email', '' ] } ] }, " +
				"{ '$not': [ { '$gt': [ '$personalInformation.contact.phone', '' ] } ] } ] }, 1, 0 ] } }, " +
			"'hasWorkExperience': { '$sum': { '$cond': [ { '$gt': [ { '$size': { '$ifNull': [ '$workExperience', [] ] } }, 0 ] }, 1, 0 ] } }, " +
			"'hasSkills': { '$sum': { '$cond': [ { '$gt': [ { '$size': { '$ifNull': [ '$keySkills', [] ] } }, 0 ] }, 1, 0 ] } }, " +
			"'hasEducation': { '$sum': { '$cond': [ { '$gt': [ { '$size': { '$ifNull': [ '$education', [] ] } }, 0 ] }, 1, 0 ] } }, " +
			"'hasCareerStartYear': { '$sum': { '$cond': [ { '$ne': [ { '$ifNull': [ '$careerStartYear', null ] }, null ] }, 1, 0 ] } }, " +
			"'hasLanguages': { '$sum': { '$cond': [ { '$gt': [ { '$size': { '$ifNull': [ '$skillsAndQualifications.languages', [] ] } }, 0 ] }, 1, 0 ] } }, " +
			"'hasCertifications': { '$sum': { '$cond': [ { '$gt': [ { '$size': { '$ifNull': [ '$certifications', [] ] } }, 0 ] }, 1, 0 ] } }, " +
			"'hasSalary': { '$sum': { '$cond': [ { '$ne': [ { '$ifNull': [ '$salaryExpectation', null ] }, null ] }, 1, 0 ] } }, " +
			"'hasLinkedin': { '$sum': { '$cond': [ { '$gt': [ '$personalInformation.contact.socialLinks.linkedin', '' ] }, 1, 0 ] } }, " +
			"'hasSummary': { '$sum': { '$cond': [ { '$gt': [ '$candidateProfileSummary', '' ] }, 1, 0 ] } } } }"
	})
	LibraryQualityReport.FieldPresenceCounts getFieldPresenceByTenantId(ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0 } }",
		"{ '$project': { 'bucket': { '$switch': { 'branches': [ " +
			"{ 'case': { '$eq': [ { '$ifNull': [ '$contentDate', null ] }, null ] }, 'then': 'UNKNOWN' }, " +
			"{ 'case': { '$gte': [ '$contentDate', { '$dateSubtract': { 'startDate': '$$NOW', 'unit': 'month', 'amount': 6 } } ] }, 'then': 'UP_TO_DATE' }, " +
			"{ 'case': { '$gte': [ '$contentDate', { '$dateSubtract': { 'startDate': '$$NOW', 'unit': 'month', 'amount': 18 } } ] }, 'then': 'REVIEW_SUGGESTED' } " +
			"], 'default': 'OUTDATED' } } } }",
		"{ '$group': { '_id': '$bucket', 'count': { '$sum': 1 } } }",
		"{ '$project': { 'name': '$_id', 'count': 1, 'percentage': { '$literal': 0.0 }, '_id': 0 } }",
		"{ '$sort': { 'count': -1 } }"
	})
	List<LibraryQualityReport.Metric> getFreshnessBucketsByTenantId(ObjectId tenantId);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0 } }",
		"{ '$group': { '_id': null, 'total': { '$sum': 1 }, " +
			"'missingClustering': { '$sum': { '$cond': [ { '$eq': [ { '$ifNull': [ '$candidateClustering.clusterConfidenceScore', null ] }, null ] }, 1, 0 ] } }, " +
			"'lowConfidence': { '$sum': { '$cond': [ { '$and': [ " +
				"{ '$ne': [ { '$ifNull': [ '$candidateClustering.clusterConfidenceScore', null ] }, null ] }, " +
				"{ '$lt': [ '$candidateClustering.clusterConfidenceScore', 0.5 ] } ] }, 1, 0 ] } }, " +
			"'avgConfidence': { '$avg': '$candidateClustering.clusterConfidenceScore' } } }"
	})
	LibraryQualityReport.ConfidenceCounts getParseConfidenceByTenantId(ObjectId tenantId);

}
