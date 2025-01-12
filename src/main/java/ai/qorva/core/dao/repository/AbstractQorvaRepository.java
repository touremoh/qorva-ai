package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.QorvaUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.springframework.util.ObjectUtils.isEmpty;

public abstract class AbstractQorvaRepository<T extends QorvaEntity> implements QorvaRepository<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractQorvaRepository.class);
    protected final MongoTemplate mongoTemplate;
    protected final Class<T> entityClass;

    protected static final String FIELD_ID = "_id";
    protected static final String FIELD_COMPANY_ID = "companyId";

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
    public Optional<T> findOneByData(String companyId, T entity) {
        // Check parameters
        Assert.notNull(entity, "Entity must not be null");

        // Build the query
        Query query = buildQueryFindOneByData(companyId, entity);

        // Execute the query
        return Optional.ofNullable(mongoTemplate.findOne(query, entityClass));
    }

    protected Query buildQueryFindOneByData(String companyId, T entity) {
        var criteria = Criteria.byExample(entity);
        if (companyId != null) {
            criteria.and(FIELD_COMPANY_ID).is(companyId);
        }
        return new Query(criteria);
    }

    @Override
    public T createOne(T entity) {
        // Check parameters
        Assert.notNull(entity, "Entity must not be null");

        // Execute the query
        return mongoTemplate.insert(entity);
    }

    @Override
    public Page<T> findMany(String companyId, int pageNumber, int pageSize) {
        // Check parameters
        Assert.notNull(companyId, "Company ID must not be null");
        Assert.isTrue(pageNumber >= 0, "Page number must be greater than or equal to 0");
        Assert.isTrue(pageSize > 0, "Page size must be greater than 0");

        // Build the query
        Query query = buildQueryFindMany(companyId, pageNumber, pageSize);

        // Execute the query
        List<T> results = mongoTemplate.find(query, entityClass);

        // Calculate the number of found elements
        long total = mongoTemplate.count(query.skip(0).limit(0), entityClass);

        // Yield results
        return new PageImpl<>(results, PageRequest.of(pageNumber, pageSize), total);
    }

    protected Query buildQueryFindMany(String companyId, int pageNumber, int pageSize) {
        return new Query(Criteria.where(FIELD_COMPANY_ID).is(companyId)).skip((long) pageNumber * pageSize).limit(pageSize);
    }

    @Override
    public Page<T> findManyByIds(List<String> ids) {
        // Check parameters
        Assert.notEmpty(ids, "IDs list must not be empty");

        // Build query
        Query query = new Query(Criteria.where(FIELD_ID).in(ids));

        // Execute query
        List<T> results = mongoTemplate.find(query, entityClass);

        // Yield results
        return new PageImpl<>(results, PageRequest.of(0, ids.size()), results.size());
    }

    @Override
    public Page<T> findMany(String companyId, int pageNumber, int pageSize, String searchTerms) throws QorvaException {
        // Check parameters
        Assert.notNull(companyId, "Company ID must not be null");
        Assert.isTrue(pageNumber >= 0, "Page number must be greater than or equal to 0");
        Assert.isTrue(pageSize > 0, "Page size must be greater than 0");

        // Build query criteria
        var query = this.buildQueryFindManyByData(companyId, pageNumber, pageSize, searchTerms);

        // Run query
        List<T> results = mongoTemplate.find(query, entityClass);

        // Render results
        return new PageImpl<>(results, PageRequest.of(pageNumber, pageSize), results.size());
    }

    protected Query buildQueryFindManyByData(String companyId, int pageNumber, int pageSize, String searchTerms) {
        return TextQuery
            .queryText(TextCriteria.forDefaultLanguage().matching(searchTerms))
            .addCriteria(Criteria.where(FIELD_COMPANY_ID).is(companyId))
            .with(PageRequest.of(pageNumber, pageSize));
    }

    @Override
    public Optional<T> updateOne(String id, T entity) {
        // Check parameters
        Assert.notNull(id, "ID must not be null");
        Assert.notNull(entity, "Entity must not be null");

        // Build query
        Query query = new Query(Criteria.where(FIELD_ID).is(id));

        // Execute query
        mongoTemplate.updateFirst(query, this.mapFieldsUpdateOne(entity), entityClass);

        // Yield results
        return findOneById(id);
    }

    protected Update mapFieldsUpdateOne(T entity) {
        return new Update();
    }



    @Override
    public boolean deleteOneById(String id) {
        // Check parameters
        Assert.notNull(id, "ID must not be null");

        // Build query
        Query query = new Query(Criteria.where(FIELD_ID).is(id));

        // Execute query and render results
        return mongoTemplate.remove(query, entityClass).getDeletedCount() > 0;
    }

    @Override
    public boolean existsByData(String companyId, T entity) {
        // Check parameters
        Assert.notNull(entity, "Entity must not be null");
        Assert.notNull(companyId, "Company ID must not be null");

        // Build query
        Query query = buildQueryFindOneByData(companyId, entity);

        // Execute query and render results
        return mongoTemplate.exists(query, entityClass);
    }
}
