package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.StripeEventLog;
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
public class StripeEventLogRepositoryImpl implements QorvaRepositorySpecification<StripeEventLog> {

	private final MongoSpecificationExecutorImpl<StripeEventLog> delegate;

	@Autowired
	public StripeEventLogRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, StripeEventLog.class);
	}

	@Override
	public List<StripeEventLog> findAll(MongoSpecification<StripeEventLog> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<StripeEventLog> findAll(MongoSpecification<StripeEventLog> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<StripeEventLog> findAll(MongoSpecification<StripeEventLog> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<StripeEventLog> findOne(MongoSpecification<StripeEventLog> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<StripeEventLog> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<StripeEventLog> specification) {
		return this.delegate.count(specification);
	}
}
