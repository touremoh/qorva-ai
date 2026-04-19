package ai.qorva.core.service;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import ai.qorva.core.qbe.QorvaQueryBuilder;
import ai.qorva.core.security.TenantContextHolder;
import io.jsonwebtoken.lang.Strings;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static ai.qorva.core.enums.QorvaErrorsEnum.RESOURCE_NOT_FOUND;

@Slf4j
public abstract class AbstractQorvaService<D extends QorvaDTO, E extends QorvaEntity>
    implements QorvaService<D> {

    protected final QorvaRepository<E> repository;
    protected final AbstractQorvaMapper<E, D> mapper;
    protected final QorvaQueryBuilder<E> queryBuilder;

    protected AbstractQorvaService(QorvaRepository<E> repository, AbstractQorvaMapper<E, D> mapper, QorvaQueryBuilder<E> queryBuilder) {
        this.repository = repository;
        this.mapper = mapper;
		this.queryBuilder = queryBuilder;
	}

    // -------------------------------------------------------------------------
    // Tenant context helper
    // -------------------------------------------------------------------------

    /**
     * Returns the tenant ID of the current request, or {@code null} when there is no
     * authenticated context (e.g. login, registration, Stripe webhook).
     */
    protected String getCurrentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    /**
     * Enforces tenant ownership when a tenant context is present.
     * If no context exists (public/system flows such as login or webhook processing),
     * the check is skipped — Spring Security already protects those routes at the HTTP layer.
     */
    protected void assertBelongsToCurrentTenant(E entity) throws QorvaException {
        var currentTenantId = getCurrentTenantId();
        if (!Strings.hasText(currentTenantId)) {
            // No authenticated context — public or internal/system call; skip ownership check.
            return;
        }
        if (!currentTenantId.equals(entity.getTenantId())) {
            log.warn("Cross-tenant access attempt: resource {} belongs to tenant {} but current tenant is {}",
                entity.getId(), entity.getTenantId(), currentTenantId);
            throw new QorvaException(
                QorvaErrorsEnum.FORBIDDEN.getMessage(),
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus().value(),
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus()
            );
        }
    }

    // -------------------------------------------------------------------------
    // findOneById
    // -------------------------------------------------------------------------

    @Override
    public D findOneById(String id) throws QorvaException {
        try {
            preProcessFindOneById(id);

            E entity = this.repository
                .findById(new ObjectId(id))
                .orElseThrow(() -> new QorvaException(
                    RESOURCE_NOT_FOUND.getMessage(),
                    RESOURCE_NOT_FOUND.getHttpStatus().value(),
                    RESOURCE_NOT_FOUND.getHttpStatus())
                );

            // Tenant isolation: reject if entity belongs to a different tenant
            assertBelongsToCurrentTenant(entity);

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
    public D findOneByData(D requestData) throws QorvaException {
        try {
            preProcessFindOneByData(requestData);
            E entity = this.processOneByData(requestData);
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
        return this.repository
            .findOne(this.queryBuilder.exampleOf(this.mapper.map(dto)))
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
    @Transactional
    public D createOne(D requestData) throws QorvaException {
        try {
            preProcessCreateOne(requestData);
            E savedEntity = this.repository.insert(this.mapper.map(requestData));
            postProcessCreateOne(savedEntity);
            return renderCreateOne(savedEntity);
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
    public Page<D> findAll(D dto, int pageNumber, int pageSize) throws QorvaException {
        try {
            preProcessFindAll(dto, pageSize, pageNumber);
            Page<E> entities = this.processFindAll(dto, pageSize, pageNumber);
            postProcessFindAll(entities);
            return renderFindAll(entities);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error finding resources with pagination");
        }
    }

    protected void preProcessFindAll(D dto, int pageSize, int pageNumber) throws QorvaException {
        Assert.notNull(dto, "Request Criteria must not be null");

        if (Objects.isNull(dto.getTenantId()) || dto.getTenantId().isEmpty()) {
            throw new QorvaException("Tenant ID must not be null or empty");
        }

        Assert.isTrue(pageNumber >= 0, "Page number must be greater than or equal to 0");
        Assert.isTrue(pageSize > 0 && pageSize <= 100, "Page size must be between 1 and 100");
    }

    protected Page<E> processFindAll(D dto, int pageSize, int pageNumber) throws QorvaException {
        var queryExample = this.queryBuilder.exampleOf(this.mapper.map(dto));
        var pageable = PageRequest.of(pageNumber, pageSize, Sort.by("lastUpdatedAt").descending());
        return this.repository.findAll(queryExample, pageable);
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
            var tenantId = getCurrentTenantId();
            // When a tenant context is present, filter by tenant; otherwise fall back to unfiltered
            // (public/system callers — Spring Security already restricts which routes reach here).
            List<E> entities = Strings.hasText(tenantId)
                ? this.repository.findByIdInAndTenantId(ids, tenantId)
                : this.repository.findByIdIn(ids);
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
    @Transactional
    public D updateOne(String id, D requestData) throws QorvaException {
        try {
            preProcessUpdateOne(id, requestData);
            E updatedEntity = this.repository.save(mapper.map(requestData));
            postProcessUpdateOne(updatedEntity);
            return renderUpdateOne(updatedEntity);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error updating resource with ID: " + id);
        }
    }

    protected void preProcessUpdateOne(String id, D requestData) throws QorvaException {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(requestData, "Input Data must not be null");

        // Fetch existing entity and verify tenant ownership before allowing the update
        E existing = this.repository
            .findById(new ObjectId(id))
            .orElseThrow(() -> new QorvaException(
                RESOURCE_NOT_FOUND.getMessage(),
                RESOURCE_NOT_FOUND.getHttpStatus().value(),
                RESOURCE_NOT_FOUND.getHttpStatus())
            );
        assertBelongsToCurrentTenant(existing);

        // Prevent the caller from overriding the tenantId on the saved document
        requestData.setTenantId(existing.getTenantId());
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
            postProcessDeleteOneById(id);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error deleting resource with ID: " + id);
        }
    }

    protected void preProcessDeleteOneById(String id, String tenantId) throws QorvaException {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(tenantId, "Tenant id must not be null");

        var entity = Optional.ofNullable(this.findOneById(id))
            .orElseThrow(() -> new QorvaException("Resource not found with id: " + id));

        // findOneById already calls assertBelongsToCurrentTenant, but we also guard here
        // via the explicit tenantId parameter for callers that supply it directly.
        if (!entity.getTenantId().equals(tenantId)) {
            log.warn("Resource {} does not belong to tenant {}", id, tenantId);
            throw new QorvaException(
                "Impossible to delete this resource",
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus().value(),
                QorvaErrorsEnum.FORBIDDEN.getHttpStatus()
            );
        }
    }

    protected void postProcessDeleteOneById(String id) {
    }

    // -------------------------------------------------------------------------
    // existsByData
    // -------------------------------------------------------------------------

    @Override
    public boolean existsByData(D requestData) throws QorvaException {
        try {
            preProcessExistsByData(requestData);
            boolean exists = this.repository.exists(this.queryBuilder.exampleOf(mapper.map(requestData)));
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
