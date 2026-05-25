package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.UsageMonitoring;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.UsageMonitoringSpecifications;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UsageMonitoringQueryBuilder implements QorvaQueryBuilder<UsageMonitoring> {

    @Override
    public MongoSpecification<UsageMonitoring> buildQuery(Map<String, String> params) {
        return MongoSpecification
            .where(UsageMonitoringSpecifications.tenantIdEquals(params.get("tenantId")))
            .and(UsageMonitoringSpecifications.subscriptionTierEquals(params.get("subscriptionTier")));
    }
}
