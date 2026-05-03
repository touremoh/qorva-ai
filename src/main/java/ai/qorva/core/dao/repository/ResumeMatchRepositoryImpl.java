package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ResumeMatch;
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
public class ResumeMatchRepositoryImpl implements QorvaRepositorySpecification<ResumeMatch> {

	private final MongoSpecificationExecutorImpl<ResumeMatch> delegate;

	@Autowired
	public ResumeMatchRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, ResumeMatch.class);
	}

	@Override
	public List<ResumeMatch> findAll(MongoSpecification<ResumeMatch> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<ResumeMatch> findAll(MongoSpecification<ResumeMatch> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<ResumeMatch> findAll(MongoSpecification<ResumeMatch> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<ResumeMatch> findOne(MongoSpecification<ResumeMatch> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<ResumeMatch> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<ResumeMatch> specification) {
		return this.delegate.count(specification);
	}
}
