package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ResumeMatch;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ResumeMatchRepository extends QorvaRepository<ResumeMatch> {
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
}
