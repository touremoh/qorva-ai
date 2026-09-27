package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.UsageMonitoring;
import org.springframework.data.mongodb.core.query.Criteria;

public final class UsageMonitoringSpecifications {

    private UsageMonitoringSpecifications() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static MongoSpecification<UsageMonitoring> subscriptionTierEquals(String tier) {
        if (tier == null || tier.isBlank()) return MongoSpecifications.empty();
        return () -> Criteria.where("subscriptionTier").is(tier);
    }
}
