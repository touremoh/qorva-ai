package ai.qorva.core.service;

import ai.qorva.core.dao.entity.UsageMonitoring;
import ai.qorva.core.dao.repository.UsageMonitoringRepository;
import ai.qorva.core.dao.repository.UsageMonitoringRepositoryImpl;
import ai.qorva.core.dao.querybuilder.UsageMonitoringQueryBuilder;
import ai.qorva.core.dto.UsageMonitoringDTO;
import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.dto.common.UsageFeatureMetrics;
import ai.qorva.core.dto.common.UsageFeatures;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UsageMonitoringMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class UsageMonitoringService extends AbstractQorvaService<UsageMonitoringDTO, UsageMonitoring> {

    public enum FeatureKey {
        SCREENING_ACTIONS("features.screeningActions"),
        AI_RESUME_CHATS("features.aiResumeChats"),
        TALENT_INTELLIGENCE_QUERIES("features.talentIntelligenceQueries");

        private final String fieldPath;

        FeatureKey(String fieldPath) {
            this.fieldPath = fieldPath;
        }

        public String consumedPath() {
            return fieldPath + ".consumed";
        }

        public String cumulativePath() {
            return fieldPath + ".cumulative";
        }

        public String limitPath() {
            return fieldPath + ".limit";
        }
    }

    private final UsageMonitoringRepository usageMonitoringRepository;
    private final UsageMonitoringRepositoryImpl usageMonitoringRepositoryImpl;

    @Autowired
    public UsageMonitoringService(
        UsageMonitoringRepository repository,
        UsageMonitoringRepositoryImpl repositoryImpl,
        UsageMonitoringMapper mapper,
        UsageMonitoringQueryBuilder queryBuilder
    ) {
        super(repository, mapper, queryBuilder);
        this.usageMonitoringRepository = repository;
        this.usageMonitoringRepositoryImpl = repositoryImpl;
    }

    /**
     * Returns the active billing period for the given tenant, or empty if none exists.
     */
    public Optional<UsageMonitoringDTO> findCurrentPeriodByTenantId(String tenantId) {
        var now = Instant.now();
        return usageMonitoringRepository
            .findByTenantIdAndCurrentPeriodStartLessThanEqualAndCurrentPeriodEndGreaterThan(tenantId, now, now)
            .map(mapper::map);
    }

    /**
     * Creates a new billing period for the given tenant from the plan's ProductFeatures definition.
     */
    public UsageMonitoringDTO initializePeriod(
        String tenantId,
        String subscriptionTier,
        Instant periodStart,
        Instant periodEnd,
        ProductFeatures planFeatures
    ) throws QorvaException {
        // Carry forward all-time cumulative totals from the previous period
        var previousFeatures = findCurrentPeriodByTenantId(tenantId)
            .map(UsageMonitoringDTO::getFeatures)
            .orElse(null);
        var dto = new UsageMonitoringDTO();
        dto.setTenantId(tenantId);
        dto.setSubscriptionTier(subscriptionTier);
        dto.setCurrentPeriodStart(periodStart);
        dto.setCurrentPeriodEnd(periodEnd);
        dto.setFeatures(buildUsageFeatures(planFeatures, previousFeatures));
        log.info("Initializing usage monitoring period for tenant={} tier={} start={} end={}",
            tenantId, subscriptionTier, periodStart, periodEnd);
        return createOne(dto);
    }

    /**
     * Atomically increments the consumed and cumulative counters for a feature in the active period.
     */
    public void incrementUsage(String tenantId, FeatureKey featureKey, int amount) {
        boolean matched = usageMonitoringRepositoryImpl.incrementFeatureUsage(
            tenantId, featureKey.consumedPath(), featureKey.cumulativePath(), amount
        );
        if (!matched) {
            log.warn("No active usage monitoring period found for tenant={}, feature increment skipped", tenantId);
        }
    }

    /**
     * Returns true if the tenant has consumed all allowed units for the given feature in the current period.
     */
    public boolean hasExceededLimit(String tenantId, FeatureKey featureKey) {
        return findCurrentPeriodByTenantId(tenantId)
            .map(dto -> {
                var metrics = resolveMetrics(dto.getFeatures(), featureKey);
                if (metrics == null || metrics.getLimit() == null) return false;
                int consumed = metrics.getConsumed() != null ? metrics.getConsumed() : 0;
                return consumed >= metrics.getLimit();
            })
            .orElse(false);
    }

    private UsageFeatures buildUsageFeatures(ProductFeatures plan, UsageFeatures previous) {
        var limits = plan != null ? plan.getLimits() : null;
        return UsageFeatures.builder()
            .screeningActions(UsageFeatureMetrics.builder()
                .limit(limits != null ? limits.getScreeningActions() : null)
                .consumed(0)
                .cumulative(cumulative(previous, FeatureKey.SCREENING_ACTIONS))
                .build())
            .aiResumeChats(UsageFeatureMetrics.builder()
                .limit(limits != null ? limits.getAiResumeChats() : null)
                .consumed(0)
                .cumulative(cumulative(previous, FeatureKey.AI_RESUME_CHATS))
                .build())
            .talentIntelligenceQueries(UsageFeatureMetrics.builder()
                .limit(limits != null ? limits.getTalentIntelligenceQueries() : null)
                .consumed(0)
                .cumulative(cumulative(previous, FeatureKey.TALENT_INTELLIGENCE_QUERIES))
                .build())
            .build();
    }

    private long cumulative(UsageFeatures previous, FeatureKey key) {
        if (previous == null) return 0L;
        var metrics = resolveMetrics(previous, key);
        return metrics != null && metrics.getCumulative() != null ? metrics.getCumulative() : 0L;
    }

    private UsageFeatureMetrics resolveMetrics(UsageFeatures features, FeatureKey featureKey) {
        if (features == null) return null;
        return switch (featureKey) {
            case SCREENING_ACTIONS -> features.getScreeningActions();
            case AI_RESUME_CHATS -> features.getAiResumeChats();
            case TALENT_INTELLIGENCE_QUERIES -> features.getTalentIntelligenceQueries();
        };
    }
}

