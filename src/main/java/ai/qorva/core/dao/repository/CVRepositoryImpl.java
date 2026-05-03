package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
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
public class CVRepositoryImpl implements QorvaRepositorySpecification<CV> {

	private final MongoSpecificationExecutorImpl<CV> delegate;

	@Autowired
	public CVRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, CV.class);
	}

	@Override
	public List<CV> findAll(MongoSpecification<CV> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<CV> findAll(MongoSpecification<CV> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<CV> findAll(MongoSpecification<CV> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<CV> findOne(MongoSpecification<CV> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<CV> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<CV> specification) {
		return this.delegate.count(specification);
	}
}
