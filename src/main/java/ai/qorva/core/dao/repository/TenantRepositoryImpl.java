package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.Tenant;
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
public class TenantRepositoryImpl implements QorvaRepositorySpecification<Tenant> {

	private final MongoSpecificationExecutorImpl<Tenant> delegate;

	@Autowired
	public TenantRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, Tenant.class);
	}

	@Override
	public List<Tenant> findAll(MongoSpecification<Tenant> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<Tenant> findAll(MongoSpecification<Tenant> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<Tenant> findAll(MongoSpecification<Tenant> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<Tenant> findOne(MongoSpecification<Tenant> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<Tenant> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<Tenant> specification) {
		return this.delegate.count(specification);
	}
}
