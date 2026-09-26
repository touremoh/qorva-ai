package ai.qorva.core.service;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import ai.qorva.core.dao.querybuilder.QorvaQueryBuilder;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.MongoSpecifications;
import org.springframework.data.mongodb.core.query.Criteria;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.security.TenantScope;
import io.jsonwebtoken.lang.Strings;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.data.domain.ExampleMatcher;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static ai.qorva.core.enums.QorvaErrorsEnum.RESOURCE_NOT_FOUND;

@Slf4j
public abstract class AbstractQorvaService<D extends QorvaDTO, E extends QorvaEntity>
    implements QorvaService<D> {

    protected final QorvaRepository<E> repository;
    protected final AbstractQorvaMapper<E, D> mapper;
    protected final QorvaQueryBuilder<E> queryBuilder;

    private final ThreadLocal<D> existingDTOForUpdate = new ThreadLocal<>();

    protected AbstractQorvaService(QorvaRepository<E> repository, AbstractQorvaMapper<E, D> mapper, QorvaQueryBuilder<E> queryBuilder) {
        this.repository = repository;
        this.mapper = mapper;
		this.queryBuilder = queryBuilder;
	}

    protected D getExistingForUpdate() {
        return existingDTOForUpdate.get();
    }

    // -------------------------------------------------------------------------
    // Tenant context helper
    // -------------------------------------------------------------------------

    /** The tenant in scope ({@link TenantScope}), or {@code null} outside one. */
    protected String getCurrentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    /** The field holding the owning tenant; the Tenant resource itself overrides this with its id. */
    protected String tenantField() {
        return "tenantId";
    }

    /**
     * The documents this code may see: those of the tenant in scope. Inside a declared system scope
     * ({@link TenantScope#runAsSystem}) that is every document; anywhere else without a tenant it is
     * reported by {@link TenantScope#missing} (refused when fail-closed, unfiltered otherwise).
     */
    protected MongoSpecification<E> inTenantScope() {
        var tenantId = getCurrentTenantId();
        if (Strings.hasText(tenantId)) {
            return () -> Criteria.where(tenantField()).is(tenantId);
        }
        if (!TenantScope.isSystem()) {
            TenantScope.missing(getClass().getSimpleName() + " query");
        }
        return MongoSpecifications.empty();
    }

    /** The document with this id if it belongs to the tenant in scope — the tenant is part of the query. */
    protected Optional<E> findOwned(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return Optional.empty();
        }
        MongoSpecification<E> byId = () -> Criteria.where("_id").is(new ObjectId(id));
        var found = this.repository.findOne(MongoSpecifications.allOf(byId, inTenantScope()));
        if (found.isEmpty() && Strings.hasText(getCurrentTenantId()) && this.repository.existsById(new ObjectId(id))) {
            // Same answer as a missing id (no existence leak), but worth a line in the logs.
            log.warn("Cross-tenant access attempt: {} {} is not in tenant {}", getClass().getSimpleName(), id, getCurrentTenantId());
        }
        return found;
    }

    private E requireOwned(String id) throws QorvaException {
        return findOwned(id).orElseThrow(() -> new QorvaException(
            RESOURCE_NOT_FOUND.getMessage(),
            RESOURCE_NOT_FOUND.getHttpStatus().value(),
            RESOURCE_NOT_FOUND.getHttpStatus()));
    }

    // -------------------------------------------------------------------------
    // findOneById
    // -------------------------------------------------------------------------

    @Override
    public D findOneById(String id) throws QorvaException {
        try {
            preProcessFindOneById(id);

            E entity = requireOwned(id);

            postProcessFindOneById(entity);
            return renderFindOneById(entity);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error finding resource by ID: " + id);
        }
    }

    protected void preProcessFindOneById(String id) {
        Objects.requireNonNull(id, "id must not be null");
    }

    protected void postProcessFindOneById(E entity) {
    }

    protected D renderFindOneById(E entity) {
        return mapper.map(entity);
    }

    // -------------------------------------------------------------------------
    // findOneByData
    // -------------------------------------------------------------------------

    @Override
    public D findOneByCriteria(D searchCriteria) throws QorvaException {
        try {
            preProcessFindOneByData(searchCriteria);
            E entity = this.processOneByData(searchCriteria);
            postProcessFindOneByData(entity);
            return renderFindOneByData(entity);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error finding resource by data");
        }
    }

    protected void preProcessFindOneByData(D requestData) {
        Assert.notNull(requestData, "Request Data must not be null");
        Assert.notNull(requestData.getTenantId(), "Tenant ID must not be null");
    }

    protected E processOneByData(D dto) throws QorvaException {
        var entity = this.mapper.map(dto);
        var example = Example.of(entity, ExampleMatcher.matching().withIgnoreNullValues());
        return this.repository
            .findOne(example)
            .orElseThrow(() -> new QorvaException(
                RESOURCE_NOT_FOUND.getMessage(),
                RESOURCE_NOT_FOUND.getHttpStatus().value(),
                RESOURCE_NOT_FOUND.getHttpStatus())
            );
    }

    protected void postProcessFindOneByData(E entity) {
    }

    protected D renderFindOneByData(E entity) {
        return mapper.map(entity);
    }

    // -------------------------------------------------------------------------
    // createOne
    // -------------------------------------------------------------------------

    @Override
    public D createOne(D newResource) throws QorvaException {
        try {
            preProcessCreateOne(newResource);
            E inserted = this.repository.insert(this.mapper.map(newResource));
            postProcessCreateOne(inserted);
            return renderCreateOne(inserted);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error creating resource");
        }
    }

    protected void preProcessCreateOne(D dto) throws QorvaException {
        Assert.notNull(dto, "requestData must not be null");
        Assert.notNull(dto.getTenantId(), "Tenant ID is mandatory");
    }

    protected void postProcessCreateOne(E entity) {
    }

    protected D renderCreateOne(E entity) {
        return mapper.map(entity);
    }

    // -------------------------------------------------------------------------
    // saveAll
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public List<D> saveAll(List<D> docs) throws QorvaException {
        preSaveAll(docs);
        var entities = saveAllDocuments(docs);
        postSaveAll(entities);
        return renderSaveAll(entities);
    }

    protected void preSaveAll(List<D> docs) throws QorvaException {
        Assert.notNull(docs, "Docs must not be null");
        Assert.isTrue(!docs.isEmpty(), "Docs must not be empty");

        for (D doc : docs) {
            if (Objects.isNull(doc.getTenantId())) {
                throw new QorvaException("Tenant ID must not be null");
            }
        }
    }

    protected List<E> saveAllDocuments(List<D> docs) {
        return this.repository.saveAll(docs.stream().map(mapper::map).toList());
    }

    protected void postSaveAll(List<E> entities) {
        log.debug("Post persist all: {} entities persisted", entities.size());
    }

    protected List<D> renderSaveAll(List<E> entities) {
        return entities.stream().map(mapper::map).toList();
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Override
    public Page<D> findAll(Map<String, String> params) throws QorvaException {
        try {
            preProcessFindAll(params);
            Page<E> entities = processFindAll(params);
            postProcessFindAll(entities);
            return renderFindAll(entities);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error finding resources with pagination");
        }
    }

    protected void preProcessFindAll(Map<String, String> params) throws QorvaException {
        Assert.notNull(params, "Request params must not be null");
        String tenantId = params.get("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            throw new QorvaException("Tenant ID must not be null or empty");
        }
        int pageNumber = Integer.parseInt(params.getOrDefault("pageNumber", "0"));
        int pageSize = Integer.parseInt(params.getOrDefault("pageSize", "25"));
        Assert.isTrue(pageNumber >= 0, "Page number must be greater than or equal to 0");
        Assert.isTrue(pageSize > 0 && pageSize <= 100, "Page size must be between 1 and 100");
    }

    protected Page<E> processFindAll(Map<String, String> params) throws QorvaException {
        int pageNumber = Integer.parseInt(params.getOrDefault("pageNumber", "0"));
        int pageSize = Integer.parseInt(params.getOrDefault("pageSize", "25"));
        var pageable = PageRequest.of(pageNumber, pageSize, Sort.by("lastUpdatedAt").descending());
        // The tenant criterion is added here, whatever the resource's query builder does with the params.
        return this.repository.findAll(MongoSpecifications.allOf(this.queryBuilder.buildQuery(params), inTenantScope()), pageable);
    }

    protected void postProcessFindAll(Page<E> entities) throws QorvaException {
    }

    protected Page<D> renderFindAll(Page<E> entities) {
        List<D> foundDocuments = entities.getContent().stream().map(mapper::map).toList();
        return new PageImpl<>(foundDocuments, entities.getPageable(), entities.getTotalElements());
    }

    // -------------------------------------------------------------------------
    // findAllByIds  (tenant-scoped)
    // -------------------------------------------------------------------------

    @Override
    public List<D> findAllByIds(List<String> ids) throws QorvaException {
        try {
            preProcessFindAllByIds(ids);
            MongoSpecification<E> byIds = () -> Criteria.where("_id").in(ids.stream().filter(ObjectId::isValid).map(ObjectId::new).toList());
            List<E> entities = this.repository.findAll(MongoSpecifications.allOf(byIds, inTenantScope()));
            postProcessFindAllByIds(entities);
            return renderFindAll(entities);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error finding resources by IDs");
        }
    }

    protected void preProcessFindAllByIds(List<String> ids) {
    }

    protected void postProcessFindAllByIds(List<E> entities) throws QorvaException {
    }

    protected List<D> renderFindAll(List<E> entities) {
        return entities.stream().map(mapper::map).toList();
    }

    // -------------------------------------------------------------------------
    // updateOne  (tenant ownership verified before save)
    // -------------------------------------------------------------------------

    @Override
    public D updateOne(String id, D newResource) throws QorvaException {
        try {
            preProcessUpdateOne(id, newResource);
            E updatedEntity = this.repository.save(mapper.map(newResource));
            postProcessUpdateOne(updatedEntity);
            return renderUpdateOne(updatedEntity);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error updating resource with ID: " + id);
        } finally {
            existingDTOForUpdate.remove();
        }
    }

    protected void preProcessUpdateOne(String id, D newResource) throws QorvaException {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(newResource, "Input Data must not be null");

        // Only a document of the tenant in scope can be updated; anything else is simply not found.
        E existing = requireOwned(id);

        // The document saved is the one whose ownership was just checked: an id in the payload
        // must never redirect the write to another document (or another tenant's).
        newResource.setId(existing.getId());
        // Prevent the caller from overriding the tenantId on the saved document
        newResource.setTenantId(existing.getTenantId());

        // Cache the existing DTO so child overrides can merge without a second DB round-trip
        existingDTOForUpdate.set(renderFindOneById(existing));
    }

    protected void postProcessUpdateOne(E entity) {
    }

    protected D renderUpdateOne(E entity) {
        return mapper.map(entity);
    }

    // -------------------------------------------------------------------------
    // deleteOneById  (tenant ownership already verified)
    // -------------------------------------------------------------------------

    @Override
    public void deleteOneById(String id, String tenantId) throws QorvaException {
        try {
            preProcessDeleteOneById(id, tenantId);
            this.repository.deleteById(new ObjectId(id));
            postProcessDeleteOneById(id, tenantId);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error deleting resource with ID: " + id);
        }
    }

    protected void preProcessDeleteOneById(String id, String tenantId) throws QorvaException {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(tenantId, "Tenant id must not be null");

        var entity = requireOwned(id);

        // The caller's tenant must also be the owner (it is the tenant in scope for API calls).
        if (!tenantId.equals(entity.getTenantId())) {
            log.warn("Resource {} does not belong to tenant {}", id, tenantId);
            throw new QorvaException(
                "Impossible to delete this resource",
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus().value(),
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus()
            );
        }
    }

    protected void postProcessDeleteOneById(String id, String tenantId) throws QorvaException {
    }

    // -------------------------------------------------------------------------
    // existsByData
    // -------------------------------------------------------------------------

    @Override
    public boolean existsByData(D requestData) throws QorvaException {
        try {
            preProcessExistsByData(requestData);
            var entity = this.mapper.map(requestData);
            var example = Example.of(entity, ExampleMatcher.matching().withIgnoreNullValues());
            boolean exists = this.repository.exists(example);
            postProcessExistsByData(requestData, exists);
            return exists;
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error checking existence of resource");
        }
    }

    protected void preProcessExistsByData(D requestData) throws QorvaException {
        Assert.notNull(requestData, "Input Data must not be null");
        Assert.notNull(requestData.getTenantId(), "Tenant ID must not be null");
    }

    protected void postProcessExistsByData(D input, boolean exists) {
    }

    // -------------------------------------------------------------------------
    // countAll
    // -------------------------------------------------------------------------

    @Override
    public long countAll(String tenantId) throws QorvaException {
        if (!Strings.hasText(tenantId)) {
            log.warn("Tenant ID is empty");
            throw new QorvaException("Tenant ID is empty");
        }
        return this.repository.countAllByTenantId(tenantId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    protected QorvaException wrapException(Exception e, String message) {
        log.error(message, e);
        return new QorvaException(message, e);
    }
}
