package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.PendingEmailNotification;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.MongoSpecificationExecutorImpl;
import ai.qorva.core.dao.specifications.QorvaRepositorySpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PendingEmailNotificationRepositoryImpl implements QorvaRepositorySpecification<PendingEmailNotification> {

    private final MongoSpecificationExecutorImpl<PendingEmailNotification> delegate;

    @Autowired
    public PendingEmailNotificationRepositoryImpl(MongoTemplate template) {
        this.delegate = new MongoSpecificationExecutorImpl<>(template, PendingEmailNotification.class);
    }

    @Override
    public List<PendingEmailNotification> findAll(MongoSpecification<PendingEmailNotification> specification) {
        return delegate.findAll(specification);
    }

    @Override
    public List<PendingEmailNotification> findAll(MongoSpecification<PendingEmailNotification> specification, Sort sort) {
        return delegate.findAll(specification, sort);
    }

    @Override
    public Page<PendingEmailNotification> findAll(MongoSpecification<PendingEmailNotification> specification, Pageable pageable) {
        return delegate.findAll(specification, pageable);
    }

    @Override
    public Optional<PendingEmailNotification> findOne(MongoSpecification<PendingEmailNotification> specification) {
        return delegate.findOne(specification);
    }

    @Override
    public boolean exists(MongoSpecification<PendingEmailNotification> specification) {
        return delegate.exists(specification);
    }

    @Override
    public long count(MongoSpecification<PendingEmailNotification> specification) {
        return delegate.count(specification);
    }
}
