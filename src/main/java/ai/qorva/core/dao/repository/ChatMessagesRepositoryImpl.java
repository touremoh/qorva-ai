package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ChatMessage;
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
public class ChatMessagesRepositoryImpl implements QorvaRepositorySpecification<ChatMessage> {

	private final MongoSpecificationExecutorImpl<ChatMessage> delegate;

	@Autowired
	public ChatMessagesRepositoryImpl(MongoTemplate template) {
		this.delegate = new MongoSpecificationExecutorImpl<>(template, ChatMessage.class);
	}

	@Override
	public List<ChatMessage> findAll(MongoSpecification<ChatMessage> specification) {
		return delegate.findAll(specification);
	}

	@Override
	public List<ChatMessage> findAll(MongoSpecification<ChatMessage> specification, Sort sort) {
		return this.delegate.findAll(specification, sort);
	}

	@Override
	public Page<ChatMessage> findAll(MongoSpecification<ChatMessage> specification, Pageable pageable) {
		return this.delegate.findAll(specification, pageable);
	}

	@Override
	public Optional<ChatMessage> findOne(MongoSpecification<ChatMessage> specification) {
		return this.delegate.findOne(specification);
	}

	@Override
	public boolean exists(MongoSpecification<ChatMessage> specification) {
		return this.delegate.exists(specification);
	}

	@Override
	public long count(MongoSpecification<ChatMessage> specification) {
		return this.delegate.count(specification);
	}
}
