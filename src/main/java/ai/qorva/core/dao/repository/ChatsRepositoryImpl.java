package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.Chat;
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
public class ChatsRepositoryImpl implements QorvaRepositorySpecification<Chat> {

	private final MongoSpecificationExecutorImpl<Chat> delegate;

	@Autowired
	public ChatsRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, Chat.class);
	}

	@Override
	public List<Chat> findAll(MongoSpecification<Chat> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<Chat> findAll(MongoSpecification<Chat> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<Chat> findAll(MongoSpecification<Chat> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<Chat> findOne(MongoSpecification<Chat> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<Chat> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<Chat> specification) {
		return this.delegate.count(specification);
	}
}
