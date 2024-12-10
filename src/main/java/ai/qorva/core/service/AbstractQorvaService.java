package ai.qorva.core.service;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AbstractQorvaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;

import static ai.qorva.core.enums.QorvaErrorsEnum.RESOURCE_NOT_FOUND;
import static ai.qorva.core.enums.QorvaErrorsEnum.VALIDATION_ERROR;

@Slf4j
public abstract class AbstractQorvaService<D extends QorvaDTO, E extends QorvaEntity>
    implements QorvaService<D> {

    protected final QorvaRepository<E> repository;
    protected final AbstractQorvaMapper<E, D> mapper;

    protected AbstractQorvaService(QorvaRepository<E> repository, AbstractQorvaMapper<E, D> mapper) {
        this.repository = repository;
        this.mapper = mapper;
	}

    @Override
    public D findOneById(String id) throws QorvaException {
        try {
            // PreProcess
            preProcessFindOneById(id);

            // Process
            E entity = repository
                .findOneById(id)
                .orElseThrow(() -> new QorvaException(
                    RESOURCE_NOT_FOUND.getMessage(),
                    RESOURCE_NOT_FOUND.getHttpStatus().value(),
                    RESOURCE_NOT_FOUND.getHttpStatus())
                );

            // Post Process
            postProcessFindOneById(entity);

            // Render results
            return renderFindOneById(entity);
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
    public D findOneByData(D input) throws QorvaException {
        try {
            // Pre Process
            preProcessFindOneByData(input);

            // Process
            E entity = repository
                .findOneByData(mapper.map(input))
                .orElseThrow(() -> new QorvaException(
                    RESOURCE_NOT_FOUND.getMessage(),
                    RESOURCE_NOT_FOUND.getHttpStatus().value(),
                    RESOURCE_NOT_FOUND.getHttpStatus())
                );

            // Post Process
            postProcessFindOneByData(entity);

            // Render results
            return renderFindOneByData(entity);
        } catch (Exception e) {
            throw wrapException(e, "Error finding resource by data");
        }
    }

    protected void preProcessFindOneByData(D input) {
        Assert.notNull(input, "Input Data must not be null");
    }

    protected void postProcessFindOneByData(E entity) {
        // Optional subclass-specific post-processing
    }

    protected D renderFindOneByData(E entity) {
        return mapper.map(entity);
    }

    @Override
    public D createOne(D input) throws QorvaException {
        try {
            // Pre Process
            preProcessCreateOne(input);

            // Process
            E savedEntity = repository.createOne(mapper.map(input));

            // Post Process
            postProcessCreateOne(savedEntity);

            // Render results
            return renderCreateOne(savedEntity);
        } catch (Exception e) {
            throw wrapException(e, "Error creating resource");
        }
    }

    protected void preProcessCreateOne(D input) throws QorvaException {
        Assert.notNull(input, "Input Data must not be null");
    }

    protected void postProcessCreateOne(E entity) {
        // Optional subclass-specific post-processing
    }

    protected D renderCreateOne(E entity) {
        return mapper.map(entity);
    }

    @Override
    public Page<D> findMany(int pageNumber, int pageSize) throws QorvaException {
        try {
            // Pre Process
            preProcessFindMany(pageNumber, pageSize);

            // Process
            Page<E> entities = repository.findMany(pageNumber, pageSize);

            // Post Process
            postProcessFindMany(entities);

            // Render Results
            return renderFindMany(entities);
        } catch (Exception e) {
            throw wrapException(e, "Error finding resources with pagination");
        }
    }

    protected void preProcessFindMany(int pageNumber, int pageSize) {
        Assert.isTrue(pageNumber >= 0, "Page number must be greater than or equal to 0");
        Assert.isTrue(pageSize > 0, "Page size must be greater than 0");
    }

    protected void postProcessFindMany(Page<E> entities) {
        // Optional subclass-specific post-processing
    }

    protected Page<D> renderFindMany(Page<E> entities) {
        // Map results from entities to dto
        List<D> foundDocuments = entities.getContent().stream().map(mapper::map).toList();

        // Render results
        return new PageImpl<>(foundDocuments, entities.getPageable(), entities.getTotalElements());
    }

    @Override
    public Page<D> findManyByIds(List<String> ids) throws QorvaException {
        try {
            // Pre Process
            preProcessFindManyByIds(ids);

            // Process
            Page<E> entities = repository.findManyByIds(ids);

            // Post Process
            postProcessFindManyByIds(entities);

            // Render results
            return renderFindManyByIds(entities);
        } catch (Exception e) {
            throw wrapException(e, "Error finding resources by IDs");
        }
    }

    protected void preProcessFindManyByIds(List<String> ids) {
        // Optional subclass-specific pre-processing
    }

    protected void postProcessFindManyByIds(Page<E> entities) {
        // Optional subclass-specific post-processing
    }

    protected Page<D> renderFindManyByIds(Page<E> entities) {
        // Map to DTO
        List<D> foundDocuments = entities.getContent().stream().map(mapper::map).toList();

        // Render results
        return new PageImpl<>(foundDocuments);
    }

    @Override
    public D updateOne(String id, D input) throws QorvaException {
        try {
            // Pre Process
            preProcessUpdateOne(id, input);

            // Process
            E updatedEntity = repository
                .updateOne(id, mapper.map(input))
                .orElseThrow(() -> new QorvaException(
                    RESOURCE_NOT_FOUND.getMessage(),
                    RESOURCE_NOT_FOUND.getHttpStatus().value(),
                    RESOURCE_NOT_FOUND.getHttpStatus())
                );

            // Post Process
            postProcessUpdateOne(updatedEntity);

            // Render results
            return renderUpdateOne(updatedEntity);
        } catch (Exception e) {
            throw wrapException(e, "Error updating resource with ID: " + id);
        }
    }

    protected void preProcessUpdateOne(String id, D input) throws QorvaException {
        // Check for null data
        Assert.notNull(id, "id must not be null");
        Assert.notNull(input, "Input Data must not be null");

        if (Objects.nonNull(input.getId()) && !input.getId().equals(id)) {
            throw new QorvaException(
                VALIDATION_ERROR.getMessage(),
                VALIDATION_ERROR.getHttpStatus().value(),
                VALIDATION_ERROR.getHttpStatus()
            );
        }
    }

    protected void postProcessUpdateOne(E entity) {
        // Optional subclass-specific post-processing
    }

    protected D renderUpdateOne(E entity) {
        return mapper.map(entity);
    }

    @Override
    public void deleteOneById(String id) throws QorvaException {
        try {
            // Pre Process
            preProcessDeleteOneById(id);

            // Process
            boolean deleted = repository.deleteOneById(id);
            if (!deleted) {
                throw new QorvaException("Failed to delete resource with ID: " + id);
            }

            // Post Process
            postProcessDeleteOneById(id);
        } catch (Exception e) {
            throw wrapException(e, "Error deleting resource with ID: " + id);
        }
    }

    protected void preProcessDeleteOneById(String id) {
        Assert.notNull(id, "id must not be null");
    }

    protected void postProcessDeleteOneById(String id) {
        // Optional subclass-specific post-processing
    }

    @Override
    public boolean existsByData(D input) throws QorvaException {
        try {
            // Pre Process
            preProcessExistsByData(input);

            // Process
            boolean exists = repository.existsByData(mapper.map(input));

            // Post Process
            postProcessExistsByData(input, exists);

            // Render results
            return exists;
        } catch (Exception e) {
            throw wrapException(e, "Error checking existence of resource");
        }
    }

    protected void preProcessExistsByData(D input) {
        Assert.notNull(input, "Input Data must not be null");
    }

    protected void postProcessExistsByData(D input, boolean exists) {
        // Optional subclass-specific post-processing
    }

    protected QorvaException wrapException(Exception e, String message) {
        log.error(message, e);
        return new QorvaException(message, e);
    }
}
