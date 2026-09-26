package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.MongoSpecifications;
import ai.qorva.core.dao.specifications.QorvaRepositorySpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Base class of every repository (registered as {@code repositoryBaseClass} in {@code MongoConfig}):
 * the Spring Data CRUD plus the {@link MongoSpecification} queries, implemented once for all entities.
 * A repository interface that extends {@link QorvaRepositorySpecification} gets them without a
 * hand-written {@code *RepositoryImpl}; one extending {@link OwnedLookup} gets the tenant-scoped id lookup.
 */
public class QorvaMongoRepositoryImpl<T, ID> extends SimpleMongoRepository<T, ID> implements QorvaRepositorySpecification<T>, OwnedLookup<T> {

	private final MongoOperations mongoOperations;
	private final Class<T> entityClass;

	public QorvaMongoRepositoryImpl(MongoEntityInformation<T, ID> metadata, MongoOperations mongoOperations) {
		super(metadata, mongoOperations);
		this.mongoOperations = mongoOperations;
		this.entityClass = metadata.getJavaType();
	}

	@Override
	public List<T> findAll(MongoSpecification<T> specification) {
		return mongoOperations.find(query(specification), entityClass);
	}

	@Override
	public List<T> findAll(MongoSpecification<T> specification, Sort sort) {
		return mongoOperations.find(query(specification).with(sort), entityClass);
	}

	@Override
	public Page<T> findAll(MongoSpecification<T> specification, Pageable pageable) {
		var query = query(specification);
		long total = mongoOperations.count(query, entityClass);
		var content = mongoOperations.find(query.with(pageable), entityClass);
		return new PageImpl<>(content, pageable, total);
	}

	@Override
	public Optional<T> findOne(MongoSpecification<T> specification) {
		return Optional.ofNullable(mongoOperations.findOne(query(specification), entityClass));
	}

	@Override
	public boolean exists(MongoSpecification<T> specification) {
		return mongoOperations.exists(query(specification), entityClass);
	}

	@Override
	public long count(MongoSpecification<T> specification) {
		return mongoOperations.count(query(specification), entityClass);
	}

	@Override
	public Optional<T> findByIdInTenant(String id, String tenantId) {
		if (id == null || !ObjectId.isValid(id) || tenantId == null || tenantId.isBlank()) {
			return Optional.empty();
		}
		// The entity class maps tenantId to its stored type (ObjectId or string) for the comparison.
		var query = new Query(Criteria.where("_id").is(new ObjectId(id)).and("tenantId").is(tenantId));
		return Optional.ofNullable(mongoOperations.findOne(query, entityClass));
	}

	private static <T> Query query(MongoSpecification<T> specification) {
		if (MongoSpecifications.isEmpty(specification)) {
			return new Query();
		}
		return new Query(specification.toCriteria());
	}
}
