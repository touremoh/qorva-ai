package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.JobPost;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobPostRepository extends QorvaRepository<JobPost> {

    @Query("{ 'tenantId': { $oid: '?0' }, 'status': ?1, 'matchingReportsNeeded': ?2 }")
    List<JobPost> findAllJobPostNeedingScreeningReports(String tenantId, String status, Boolean matchingReportNeeded);

    @Query(value = "{ 'tenantId': { $oid: '?0' }, 'status': ?1 }", count = true)
    long countByTenantIdAndStatus(String tenantId, String status);

    @Query(value = "{ 'tenantId': { $oid: '?0' }, 'status': ?1, 'matchingReportsNeeded': true }", count = true)
    long countAwaitingMatching(String tenantId, String status);

    /** Deletes every job post belonging to a tenant (used when purging demo data on upgrade). */
    long deleteByTenantId(String tenantId);
}
