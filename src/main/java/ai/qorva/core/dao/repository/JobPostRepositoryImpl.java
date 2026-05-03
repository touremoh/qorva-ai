package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.JobPost;
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
public class JobPostRepositoryImpl implements QorvaRepositorySpecification<JobPost> {

	private final MongoSpecificationExecutorImpl<JobPost> delegate;

	@Autowired
	public JobPostRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, JobPost.class);
	}

	@Override
	public List<JobPost> findAll(MongoSpecification<JobPost> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<JobPost> findAll(MongoSpecification<JobPost> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<JobPost> findAll(MongoSpecification<JobPost> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<JobPost> findOne(MongoSpecification<JobPost> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<JobPost> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<JobPost> specification) {
		return this.delegate.count(specification);
	}
}
