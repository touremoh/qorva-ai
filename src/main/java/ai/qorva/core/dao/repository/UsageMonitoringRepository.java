package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.UsageMonitoring;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UsageMonitoringRepository extends QorvaRepository<UsageMonitoring> {

    Optional<UsageMonitoring> findByTenantIdAndCurrentPeriodStartLessThanEqualAndCurrentPeriodEndGreaterThan(
        String tenantId, Instant periodStart, Instant periodEnd
    );

    /** Deletes usage monitoring for a tenant (used when purging demo data on upgrade). */
    long deleteByTenantId(String tenantId);
}
