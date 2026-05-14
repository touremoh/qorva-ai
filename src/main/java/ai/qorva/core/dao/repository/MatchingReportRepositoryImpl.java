package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.MatchingReport;
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
public class MatchingReportRepositoryImpl implements QorvaRepositorySpecification<MatchingReport> {

	private final MongoSpecificationExecutorImpl<MatchingReport> delegate;

	@Autowired
	public MatchingReportRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, MatchingReport.class);
	}

	@Override
	public List<MatchingReport> findAll(MongoSpecification<MatchingReport> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<MatchingReport> findAll(MongoSpecification<MatchingReport> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<MatchingReport> findAll(MongoSpecification<MatchingReport> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<MatchingReport> findOne(MongoSpecification<MatchingReport> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<MatchingReport> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<MatchingReport> specification) {
		return this.delegate.count(specification);
	}
}
