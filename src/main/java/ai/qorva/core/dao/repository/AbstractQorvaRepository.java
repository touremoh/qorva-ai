package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.dto.request.FindManyRequestCriteria;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class AbstractQorvaRepository<T extends QorvaEntity> implements QorvaRepository<T> {

    protected final MongoTemplate mongoTemplate;
    protected final Class<T> entityClass;

    protected static final String FIELD_ID = "_id";
    protected static final String FIELD_TENANT_ID = "tenantId";

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
        Assert.notNull(entity, "Input parameter must not be null");
        Assert.notNull(entity.getTenantId(), "Tenant ID must not be null");

        // Build the query
        Query query = buildQueryFindOneByData(entity);

        // Execute the query
        return Optional.ofNullable(mongoTemplate.findOne(query, entityClass));
    }

    protected Query buildQueryFindOneByData(T entity) {
        var criteria = Criteria.byExample(entity);
        if (entity.getTenantId() != null) {
            criteria.and(FIELD_TENANT_ID).is(entity.getTenantId());
        }
        return new Query(criteria);
    }

    @Override
    public T createOne(T entity) {
        // Check parameters
        Assert.notNull(entity, "Entity must not be null");
        Assert.notNull(entity.getTenantId(), "Tenant ID must not be null");

        // Execute the query
        return mongoTemplate.insert(entity);
    }

    @Override
    public Page<T> findMany(FindManyRequestCriteria requestCriteria) {
        // Build the query
        Query query = buildQueryFindMany(requestCriteria.getTenantId(), requestCriteria.getPageNumber(), requestCriteria.getPageSize());

        // Execute the query
        List<T> results = mongoTemplate.find(query, entityClass);

        // Calculate the number of found elements
        long total = mongoTemplate.count(query.skip(0).limit(0), entityClass);

        // Yield results
        return new PageImpl<>(results, PageRequest.of(requestCriteria.getPageNumber(), requestCriteria.getPageSize()), total);
    }

    protected Query buildQueryFindMany(String companyId, int pageNumber, int pageSize) {
        return new Query(Criteria.where(FIELD_TENANT_ID).is(companyId)).skip((long) pageNumber * pageSize).limit(pageSize);
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
    public Page<T> findManyByText(FindManyRequestCriteria requestCriteria) throws QorvaException {
        // Check parameters
        Assert.notNull(requestCriteria.getSearchTerms(), "Search terms for text query must not be null");

        // Build query criteria
        var query = this.buildQueryFindManyByText(requestCriteria);

        // Run query
        List<T> results = mongoTemplate.find(query, entityClass);

        // Render results
        return new PageImpl<>(results, PageRequest.of(requestCriteria.getPageNumber(), requestCriteria.getPageSize()), results.size());
    }

    protected Query buildQueryFindManyByText(FindManyRequestCriteria requestCriteria) {
        return TextQuery
            .queryText(TextCriteria.forDefaultLanguage().matching(requestCriteria.getSearchTerms()))
            .addCriteria(Criteria.where(FIELD_TENANT_ID).is(requestCriteria.getTenantId()))
            .with(PageRequest.of(requestCriteria.getPageNumber(), requestCriteria.getPageSize()));
    }

    @Override
    public Optional<T> updateOne(String id, T entity) {
        // Check parameters
        Assert.notNull(id, "ID must not be null");
        Assert.notNull(entity, "Entity must not be null");
        Assert.notNull(entity.getTenantId(), "Tenant ID must not be null");

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
    public boolean existsByData(T entity) {
        // Check parameters
        Assert.notNull(entity, "Entity must not be null");
        Assert.notNull(entity.getTenantId(), "Tenant ID must not be null");

        // Build query
        Query query = buildQueryFindOneByData(entity);

        // Execute query and render results
        return mongoTemplate.exists(query, entityClass);
    }
}
