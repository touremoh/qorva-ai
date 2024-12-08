package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.QorvaEntity;
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
        Assert.notNull(id, "ID must not be null");
        return Optional.ofNullable(mongoTemplate.findById(id, entityClass));
    }

    @Override
    public Optional<T> findOneByData(T entity) {
        Assert.notNull(entity, "Entity must not be null");
        Query query = new Query(Criteria.byExample(entity));
        return Optional.ofNullable(mongoTemplate.findOne(query, entityClass));
    }

    @Override
    public T createOne(T entity) {
        Assert.notNull(entity, "Entity must not be null");
        return mongoTemplate.insert(entity);
    }

    @Override
    public List<T> findMany(int pageNumber, int pageSize) {
        Assert.isTrue(pageNumber >= 0, "Page number must be greater than or equal to 0");
        Assert.isTrue(pageSize > 0, "Page size must be greater than 0");

        Query query = new Query().skip((long) pageNumber * pageSize).limit(pageSize);
        return mongoTemplate.find(query, entityClass);
    }

    @Override
    public List<T> findManyByIds(List<String> ids) {
        Assert.notEmpty(ids, "IDs list must not be empty");
        Query query = new Query(Criteria.where("id").in(ids));
        return mongoTemplate.find(query, entityClass);
    }

    @Override
    public Optional<T> updateOne(String id, T entity) {
        Assert.notNull(id, "ID must not be null");
        Assert.notNull(entity, "Entity must not be null");

        Query query = new Query(Criteria.where("id").is(id));
        Update update = new Update();
        // Use reflection or manually map fields to Update
        mongoTemplate.updateFirst(query, update, entityClass);
        return findOneById(id);
    }

    @Override
    public boolean deleteOneById(String id) {
        Assert.notNull(id, "ID must not be null");
        Query query = new Query(Criteria.where("id").is(id));
        return mongoTemplate.remove(query, entityClass).getDeletedCount() > 0;
    }

    @Override
    public boolean existsByData(T entity) {
        Assert.notNull(entity, "Entity must not be null");
        Query query = new Query(Criteria.byExample(entity));
        return mongoTemplate.exists(query, entityClass);
    }
}
