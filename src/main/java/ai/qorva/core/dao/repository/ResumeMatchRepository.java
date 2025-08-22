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

	Optional<ResumeMatch> findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(
		String tenantId,
		String jobPostId,
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
}
