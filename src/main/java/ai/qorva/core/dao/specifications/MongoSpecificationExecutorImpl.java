package ai.qorva.core.dao.specifications;


import org.apache.poi.ss.formula.functions.T;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;


public class MongoSpecificationExecutorImpl<T> implements MongoSpecificationExecutor<T> {

	private final MongoTemplate mongoTemplate;
	private final Class<T> entityClass;

	public MongoSpecificationExecutorImpl(MongoTemplate mongoTemplate, Class<T> entityClass) {
		this.mongoTemplate = mongoTemplate;
		this.entityClass = entityClass;
	}

	@Override
	public List<T> findAll(MongoSpecification<T> specification) {
		return this.mongoTemplate.find(buildQuery(specification), this.entityClass);
	}

	@Override
	public List<T> findAll(MongoSpecification<T> specification, Sort sort) {
		return this.mongoTemplate.find(buildQuery(specification).with(sort), this.entityClass);
	}

	@Override
	public Page<T> findAll(MongoSpecification<T> specification, Pageable pageable) {
		Query query = buildQuery(specification);
		long total = mongoTemplate.count(query, entityClass);

		query.with(pageable);
		List<T> content = mongoTemplate.find(query, entityClass);

		return new PageImpl<>(content, pageable, total);
	}

	@Override
	public Optional<T> findOne(MongoSpecification<T> specification) {
		return Optional.ofNullable(mongoTemplate.findOne(buildQuery(specification), entityClass));
	}

	@Override
	public boolean exists(MongoSpecification<T> specification) {
		return mongoTemplate.exists(buildQuery(specification), entityClass);
	}

	@Override
	public long count(MongoSpecification<T> specification) {
		return mongoTemplate.count(buildQuery(specification), entityClass);
	}

	private Query buildQuery(MongoSpecification<T> specification) {
		if (specification == null || specification instanceof MongoSpecifications.EmptyMongoSpecification) {
			return new Query();
		}

		Query query = new Query();
		query.addCriteria(specification.toCriteria());
		return query;
	}
}
