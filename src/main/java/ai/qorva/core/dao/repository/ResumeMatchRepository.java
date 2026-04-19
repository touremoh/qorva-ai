package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ResumeMatch;
import ai.qorva.core.dto.DashboardData;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeMatchRepository extends QorvaRepository<ResumeMatch> {

	@Query(value = "{ '$text': { $search: ?0 }, 'tenantId': ?1 }")
	Page<ResumeMatch> searchAll(String searchTerms, String tenantId, Pageable pageable);


	/**
	 * Count all ResumeMatch docs for this tenant, whose createdAt
	 * is between startOfMonth (inclusive) and endOfMonth (inclusive).
	 */
	long countByTenantIdAndCreatedAtBetween(
		String tenantId,
		Instant startOfMonth,
		Instant   endOfMonth
	);

	@Query(value = "{ 'tenantId': ?0, 'jobPostId': ?1, 'candidateInfo.candidateId': ?2 }")
	Optional<ResumeMatch> findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(
		ObjectId tenantId,
		ObjectId jobPostId,
		String candidateId
	);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0 } }",
		"{ '$lookup': { " +
			"'from': 'JobsPosts', " +
			"'localField': 'jobPostId', " +
			"'foreignField': '_id', " +
			"'as': 'job' } }",
		"{ '$unwind': '$job' }",
		"{ '$group': { '_id': '$job.title', 'totalMatch': { '$sum': 1 } } }",
		"{ '$project': { 'jobPostTitle': '$_id', 'totalMatch': 1, '_id': 0 } }",
		"{ '$sort': { 'totalMatch': -1 } }"
	})
	List<DashboardData.ApplicationPerJobPostReport> getApplicationsPerJobPost(ObjectId tenantId);

	/**
	 * Returns the top 5 candidates per job posting, ranked by overall score descending.
	 * Pipeline:
	 *  1. Filter by tenant
	 *  2. Sort by score desc so $push preserves ranking
	 *  3. Group by jobPostId, collecting all candidates
	 *  4. Slice to the first 5 per group
	 */
	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0 } }",
		"{ '$sort': { 'aiAnalysisReportDetails.overallSummary.score': -1 } }",
		"{ '$group': { " +
			"'_id': '$jobPostId', " +
			"'jobPostTitle': { '$first': '$jobPostTitle' }, " +
			"'topCandidates': { '$push': { " +
				"'candidateId': '$candidateInfo.candidateId', " +
				"'candidateName': '$candidateInfo.candidateName', " +
				"'score': '$aiAnalysisReportDetails.overallSummary.score' " +
			"} } " +
		"} }",
		"{ '$project': { " +
			"'jobPostTitle': 1, " +
			"'topCandidates': { '$slice': [ '$topCandidates', 5 ] }, " +
			"'_id': 0 " +
		"} }"
	})
	List<DashboardData.TopCandidatesPerJobReport> getTopCandidatesPerJobPost(ObjectId tenantId);
}
