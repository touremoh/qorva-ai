package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.QorvaEntity;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface QorvaRepository<T extends QorvaEntity> {

    /**
     * Find one document by its type.
     *
     * @param id The ID of the document.
     * @return An optional containing the document if found, or empty otherwise.
     */
    Optional<T> findOneById(String id);

    /**
     * Find one document by its type.
     *
     * @param companyId Filter out by companyId.
     * @param entity The data of the document.
     * @return An optional containing the document if found, or empty otherwise.
     */
    Optional<T> findOneByData(String companyId, T entity);

    /**
     * Create one document.
     *
     * @param entity The document to create.
     * @return The created document.
     */
    T createOne(T entity);

    /**
     * Find many documents by type with pagination.
     *
     * @param companyId Filter out by companyId to avoid returning data from other companies
     * @param pageNumber The page number.
     * @param pageSize   The number of items per page.
     * @return A pageable object containing the documents for the given page.
     */
    Page<T> findMany(String companyId, int pageNumber, int pageSize);

    /**
     * Find many documents by their IDs.
     *
     * @param ids The list of document IDs.
     * @return A pageable object containing the documents matching the IDs.
     */
    Page<T> findManyByIds( List<String> ids);

    /**
     * Update one document.
     *
     * @param id     The ID of the document to update.
     * @param entity The updated document data.
     * @return The updated document.
     */
    Optional<T> updateOne(String id, T entity);

    /**
     * Delete one document by its ID.
     *
     * @param id The ID of the document to delete.
     * @return True if the document was deleted, false otherwise.
     */
    boolean deleteOneById(String id);

    /**
     * Check if a document exists by its data.
     *
     * @param companyId Filter out by companyId to avoid returning data from other companies
     * @param entity The document data to check for existence.
     * @return True if a matching document exists, false otherwise.
     */
    boolean existsByData(String companyId, T entity);
}
