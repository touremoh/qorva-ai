package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.UsageMonitoring;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class UsageMonitoringRepositoryImpl {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public UsageMonitoringRepositoryImpl(MongoTemplate template) {
        this.mongoTemplate = template;
    }

    /**
     * Atomically increments a feature's consumed counter (current period) and cumulative counter
     * (all-time) for the active billing period of the given tenant. Returns true if a period was matched.
     */
    public boolean incrementFeatureUsage(String tenantId, String consumedPath, String cumulativePath, int amount) {
        var now = Instant.now();
        var query = Query.query(
            Criteria.where("tenantId").is(tenantId)
                .and("currentPeriodStart").lte(now)
                .and("currentPeriodEnd").gt(now)
        );
        var update = new Update()
            .inc(consumedPath, amount)
            .inc(cumulativePath, amount);
        return mongoTemplate.updateFirst(query, update, UsageMonitoring.class).getMatchedCount() > 0;
    }
}
