package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.QorvaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

public abstract class AbstractQorvaRepository<T extends QorvaEntity> implements QorvaRepository<T> {

    protected final MongoTemplate mongoTemplate;
    protected final Class<T> entityClass;

    protected AbstractQorvaRepository(MongoTemplate mongoTemplate, Class<T> entityClass) {
        this.mongoTemplate = mongoTemplate;
        this.entityClass = entityClass;
    }

    @Override
    public Optional<T> findOneById(String id) {
        // Check parameters
        Assert.notNull(id, "ID must not be null");

        // Build and execute query
        return Optional.ofNullable(mongoTemplate.findById(id, entityClass));
    }

    @Override
    public Optional<T> findOneByData(T entity) {
        // Check parameters
        Assert.notNull(entity, "Entity must not be null");

        // Build the query
        Query query = buildQueryFindOneByData(entity);

        // Execute the query
        return Optional.ofNullable(mongoTemplate.findOne(query, entityClass));
    }

    protected Query buildQueryFindOneByData(T entity) {
        return new Query(Criteria.byExample(entity));
    }

    @Override
    public T createOne(T entity) {
        // Check parameters
        Assert.notNull(entity, "Entity must not be null");

        // Execute the query
        return mongoTemplate.insert(entity);
    }

    @Override
    public Page<T> findMany(int pageNumber, int pageSize) {
        // Check parameters
        Assert.isTrue(pageNumber >= 0, "Page number must be greater than or equal to 0");
        Assert.isTrue(pageSize > 0, "Page size must be greater than 0");

        // Build the query
        Query query = new Query().skip((long) pageNumber * pageSize).limit(pageSize);

        // Execute the query
        List<T> results = mongoTemplate.find(query, entityClass);

        // Calculate the number of found elements
        long total = mongoTemplate.count(query.skip(0).limit(0), entityClass);

        // Yield results
        return new PageImpl<>(results, PageRequest.of(pageNumber, pageSize), total);
    }

    @Override
    public Page<T> findManyByIds(List<String> ids) {
        // Check parameters
        Assert.notEmpty(ids, "IDs list must not be empty");

        // Build query
        Query query = new Query(Criteria.where("id").in(ids));

        // Execute query
        List<T> results = mongoTemplate.find(query, entityClass);

        // Yield results
        return new PageImpl<>(results, PageRequest.of(0, ids.size()), results.size());
    }

    @Override
    public Optional<T> updateOne(String id, T entity) {
        // Check parameters
        Assert.notNull(id, "ID must not be null");
        Assert.notNull(entity, "Entity must not be null");

        // Build query
        Query query = new Query(Criteria.where("_id").is(id));

        // Execute query
        mongoTemplate.updateFirst(query, this.mapFieldUpdateOne(entity), entityClass);

        // Yield results
        return findOneById(id);
    }

    protected Update mapFieldUpdateOne(T entity) {
        return new Update();
    }



    @Override
    public boolean deleteOneById(String id) {
        // Check parameters
        Assert.notNull(id, "ID must not be null");

        // Build query
        Query query = new Query(Criteria.where("id").is(id));

        // Execute query and render results
        return mongoTemplate.remove(query, entityClass).getDeletedCount() > 0;
    }

    @Override
    public boolean existsByData(T entity) {
        // Check parameters
        Assert.notNull(entity, "Entity must not be null");

        // Build query
        Query query = new Query(Criteria.byExample(entity));

        // Execute query and render results
        return mongoTemplate.exists(query, entityClass);
    }
}
