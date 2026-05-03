package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.User;
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
public class UserRepositoryImpl implements QorvaRepositorySpecification<User> {

	private final MongoSpecificationExecutorImpl<User> delegate;

	@Autowired
	public UserRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, User.class);
	}

	@Override
	public List<User> findAll(MongoSpecification<User> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<User> findAll(MongoSpecification<User> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<User> findAll(MongoSpecification<User> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<User> findOne(MongoSpecification<User> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<User> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<User> specification) {
		return this.delegate.count(specification);
	}
}
