package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dto.DashboardData;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchingReportRepository extends QorvaRepository<MatchingReport> {

	@Query(value = "{ '$text': { '$search': ?0 }, 'tenantId': ?1, 'jobPostId': ?2 }")
	Page<MatchingReport> searchAll(String searchTerms, String tenantId, String jobPostId, Pageable pageable);

	@Query(value = "{ '$text': { '$search': ?0 }, 'tenantId': ?1 }")
	Page<MatchingReport> searchAll(String searchTerms, String tenantId, Pageable pageable);


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
	Optional<MatchingReport> findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(
		ObjectId tenantId,
		ObjectId jobPostId,
		String candidateId
	);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0 } }",
		"{ '$lookup': { " +
			"'from': 'job_posts', " +
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
		"{ '$sort': { 'matchingReportDetails.decisionSummary.finalScore': -1 } }",
		"{ '$group': { " +
			"'_id': '$jobPostId', " +
			"'jobPostTitle': { '$first': '$jobPostTitle' }, " +
			"'topCandidates': { '$push': { " +
				"'candidateId': '$candidateInfo.candidateId', " +
				"'candidateName': '$candidateInfo.candidateName', " +
				"'score': '$matchingReportDetails.decisionSummary.finalScore' " +
			"} } " +
		"} }",
		"{ '$project': { " +
			"'jobPostTitle': 1, " +
			"'topCandidates': { '$slice': [ '$topCandidates', 5 ] }, " +
			"'_id': 0 " +
		"} }"
	})
	Slice<DashboardData.TopCandidatesPerJobReport> getTopCandidatesPerJobPost(ObjectId tenantId, Pageable pageable);

	@Aggregation(pipeline = {
		"{ '$match': { 'tenantId': ?0 } }",
		"{ '$group': { '_id': '$jobPostId' } }",
		"{ '$group': { '_id': null, 'total': { '$sum': 1 } } }"
	})
	DashboardData.JobPostCount countDistinctJobPosts(ObjectId tenantId);

	/**
	 * Deletes all resume matches for a given tenant and job post.
	 */
	long deleteByTenantIdAndJobPostId(String tenantId, String jobPostId);

	/**
	 * Deletes all resume matches for a given tenant and candidate.
	 * @param tenantId
	 * @param candidateId
	 * @return
	 */
	long deleteByTenantIdAndCandidateInfoCandidateId(String tenantId, String candidateId);

	/** Deletes every matching report belonging to a tenant (used when purging demo data on upgrade). */
	long deleteByTenantId(String tenantId);
}
