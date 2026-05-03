package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ProductReference;
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
public class ProductReferenceRepositoryImpl implements QorvaRepositorySpecification<ProductReference> {

	private final MongoSpecificationExecutorImpl<ProductReference> delegate;

	@Autowired
	public ProductReferenceRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, ProductReference.class);
	}

	@Override
	public List<ProductReference> findAll(MongoSpecification<ProductReference> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<ProductReference> findAll(MongoSpecification<ProductReference> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<ProductReference> findAll(MongoSpecification<ProductReference> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<ProductReference> findOne(MongoSpecification<ProductReference> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<ProductReference> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<ProductReference> specification) {
		return this.delegate.count(specification);
	}
}
