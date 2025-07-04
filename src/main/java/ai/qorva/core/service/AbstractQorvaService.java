package ai.qorva.core.service;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import ai.qorva.core.qbe.QorvaQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;

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

    @Override
    public D findOneById(String id) throws QorvaException {
        try {
            // PreProcess
            preProcessFindOneById(id);

            // Process
            E entity = this.repository
                .findById(new ObjectId(id))
                .orElseThrow(() -> new QorvaException(
                    RESOURCE_NOT_FOUND.getMessage(),
                    RESOURCE_NOT_FOUND.getHttpStatus().value(),
                    RESOURCE_NOT_FOUND.getHttpStatus())
                );

            // Post Process
            postProcessFindOneById(entity);

            // Render results
            return renderFindOneById(entity);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error finding resource by ID: " + id);
        }
    }

    protected void preProcessFindOneById(String id) {
        Assert.notNull(id, "id must not be null");
    }

    protected void postProcessFindOneById(E entity) {
        // Optional subclass-specific post-processing
    }

    protected D renderFindOneById(E entity) {
        return mapper.map(entity);
    }

    @Override
    public D findOneByData(D requestData) throws QorvaException {
        try {
            // Pre Process
            preProcessFindOneByData(requestData);

            // Process
            E entity = this.processOneByData(requestData);

            // Post Process
            postProcessFindOneByData(entity);

            // Render results
            return renderFindOneByData(entity);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error finding resource by data");
        }
    }

    protected void preProcessFindOneByData(D requestData) {
        Assert.notNull(requestData,"Request Data must not be null");
        Assert.notNull(requestData.getTenantId(),"Tenant ID must not be null");
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
        // Optional subclass-specific post-processing
    }

    protected D renderFindOneByData(E entity) {
        return mapper.map(entity);
    }

    @Override
    public D createOne(D requestData) throws QorvaException {
        try {
            // Pre Process
            preProcessCreateOne(requestData);

            // Process
            E savedEntity = this.repository.insert(this.mapper.map(requestData));

            // Post Process
            postProcessCreateOne(savedEntity);

            // Render results
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
        // Optional subclass-specific post-processing
    }

    protected D renderCreateOne(E entity) {
        return mapper.map(entity);
    }

    @Override
    public List<D> saveAll(List<D> docs) throws QorvaException {
        // Pre Persist All
        preSaveAll(docs);

        // Persist All
        var entities = saveAllDocuments(docs);

        // Post Persist All
        postSaveAll(entities);

        // Render Results
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

    @Override
    public Page<D> findAll(D dto, int pageNumber, int pageSize) throws QorvaException {
        try {
            // Pre Process
            preProcessFindAll(dto, pageSize, pageNumber);

            // Process
            Page<E> entities = this.processFindAll(dto, pageSize, pageNumber);

            // Post Process
            postProcessFindAll(entities);

            // Render Results
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
            throw new QorvaException("Tenant ID must not be null or  empty");
        }

        Assert.isTrue(pageNumber >= 0, "Page number must be greater than or equal to 0");
        Assert.isTrue(pageSize > 0, "Page size must be greater than 0");
    }

    protected Page<E> processFindAll(D dto, int pageSize, int pageNumber) throws QorvaException {
        var queryExample = this.queryBuilder.exampleOf(this.mapper.map(dto));
        var pageable = Pageable.ofSize(pageSize).withPage(pageNumber);
        return this.repository.findAll(queryExample, pageable);
    }

    protected void postProcessFindAll(Page<E> entities) throws QorvaException {
        // Optional subclass-specific post-processing
    }

    protected Page<D> renderFindAll(Page<E> entities) {
        // Map results from entities to dto
        List<D> foundDocuments = entities.getContent().stream().map(mapper::map).toList();

        // Render results
        return new PageImpl<>(foundDocuments, entities.getPageable(), entities.getTotalElements());
    }

    @Override
    public List<D> findAllByIds(List<String> ids) throws QorvaException {
        try {
            // Pre Process
            preProcessFindAllByIds(ids);

            // Process
            List<E> entities = this.repository.findByIdIn(ids);

            // Post Process
            postProcessFindAllByIds(entities);

            // Render results
            return renderFindAll(entities);
        } catch (Exception e) {
            throw wrapException(e, "Error finding resources by IDs");
        }
    }

    protected void preProcessFindAllByIds(List<String> ids) {
        // Optional subclass-specific pre-processing
    }

    protected void postProcessFindAllByIds(List<E> entities) throws QorvaException {
        // Optional subclass-specific post-processing
    }

    protected List<D> renderFindAll(List<E> entities) {
        return  entities.stream().map(mapper::map).toList();
    }

    @Override
    public D updateOne(String id, D requestData) throws QorvaException {
        try {
            // Pre Process
            preProcessUpdateOne(id, requestData);

            // Process
            E updatedEntity = this.repository.save(mapper.map(requestData));

            // Post Process
            postProcessUpdateOne(updatedEntity);

            // Render results
            return renderUpdateOne(updatedEntity);
        } catch (QorvaException e) {
            throw e;
        } catch (Exception e) {
            throw wrapException(e, "Error updating resource with ID: " + id);
        }
    }

    protected void preProcessUpdateOne(String id, D requestData) throws QorvaException {
        // Check for null data
        Assert.notNull(id, "id must not be null");
        Assert.notNull(requestData, "Input Data must not be null");
    }

    protected void postProcessUpdateOne(E entity) {
        // Optional subclass-specific post-processing
    }

    protected D renderUpdateOne(E entity) {
        return mapper.map(entity);
    }

    @Override
    public void deleteOneById(String id, String tenantId) throws QorvaException {
        try {
            // Pre Process
            preProcessDeleteOneById(id, tenantId);

            // Process
            this.repository.deleteById(new ObjectId(id));

            // Post Process
            postProcessDeleteOneById(id);
        } catch (Exception e) {
            throw wrapException(e, "Error deleting resource with ID: " + id);
        }
    }

    protected void preProcessDeleteOneById(String id, String tenantId) throws QorvaException {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(tenantId, "Tenant id must not be null");
    }

    protected void postProcessDeleteOneById(String id) {
        // Optional subclass-specific post-processing
    }

    @Override
    public boolean existsByData(D requestData) throws QorvaException {
        try {
            // Pre Process
            preProcessExistsByData(requestData);

            // Process
            boolean exists = this.repository.exists(this.queryBuilder.exampleOf(mapper.map(requestData)));

            // Post Process
            postProcessExistsByData(requestData, exists);

            // Render results
            return exists;
        } catch (Exception e) {
            throw wrapException(e, "Error checking existence of resource");
        }
    }

    protected void preProcessExistsByData(D requestData) {
        Assert.notNull(requestData, "Input Data must not be null");
        Assert.notNull(requestData.getTenantId(), "Tenant ID must not be null");
    }

    protected void postProcessExistsByData(D input, boolean exists) {
        // Optional subclass-specific post-processing
    }

    protected QorvaException wrapException(Exception e, String message) {
        log.error(message, e);
        return new QorvaException(message, e);
    }
}
