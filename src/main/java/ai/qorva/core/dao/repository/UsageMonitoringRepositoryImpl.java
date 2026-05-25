package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.UsageMonitoring;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.MongoSpecificationExecutorImpl;
import ai.qorva.core.dao.specifications.QorvaRepositorySpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class UsageMonitoringRepositoryImpl implements QorvaRepositorySpecification<UsageMonitoring> {

    private final MongoSpecificationExecutorImpl<UsageMonitoring> delegate;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public UsageMonitoringRepositoryImpl(MongoTemplate template) {
        this.delegate = new MongoSpecificationExecutorImpl<>(template, UsageMonitoring.class);
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

    @Override
    public List<UsageMonitoring> findAll(MongoSpecification<UsageMonitoring> specification) {
        return delegate.findAll(specification);
    }

    @Override
    public List<UsageMonitoring> findAll(MongoSpecification<UsageMonitoring> specification, Sort sort) {
        return delegate.findAll(specification, sort);
    }

    @Override
    public Page<UsageMonitoring> findAll(MongoSpecification<UsageMonitoring> specification, Pageable pageable) {
        return delegate.findAll(specification, pageable);
    }

    @Override
    public Optional<UsageMonitoring> findOne(MongoSpecification<UsageMonitoring> specification) {
        return delegate.findOne(specification);
    }

    @Override
    public boolean exists(MongoSpecification<UsageMonitoring> specification) {
        return delegate.exists(specification);
    }

    @Override
    public long count(MongoSpecification<UsageMonitoring> specification) {
        return delegate.count(specification);
    }
}
