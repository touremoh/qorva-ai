package ai.qorva.core.service;

import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.exception.QorvaException;
import org.springframework.data.domain.Page;

import java.util.List;

public interface QorvaService<D extends QorvaDTO> {

    /**
     * Find one record by ID.
     *
     * @param id The ID of the record.
     * @return The record.
     * @throws QorvaException if the record is not found.
     */
    D findOneById(String id) throws QorvaException;

    /**
     * Find one record by its data.
     *
     * @param requestData The ID of the company that owns the data
     * @return The record.
     * @throws QorvaException if the record is not found.
     */
    D findOneByData(D requestData) throws QorvaException;

    /**
     * Create one record.
     *
     * @param input The data of the record to create.
     * @return The created record.
     * @throws QorvaException if an error occurs during creation.
     */
    D createOne(D input) throws QorvaException;

    /**
     * Persist multiple records at once.
     *
     * @param docs The data of the records to create.
     * @return records.
     * @throws QorvaException if an error occurs during creation.
     */
    List<D> saveAll(List<D> docs) throws QorvaException;

    /**
     * Find many records with pagination.
     *
     * @param dto request criteria.
     * @param pageSize request criteria.
     * @param pageNumber request criteria.
     * @return A pageable list of records.
     * @throws QorvaException if an error occurs during retrieval.
     */
    Page<D> findAll(D dto, int pageNumber, int pageSize) throws QorvaException;

    /**
     * Find many records by their IDs.
     *
     * @param ids The list of IDs.
     * @return A pageable list of records.
     * @throws QorvaException if an error occurs during retrieval.
     */
    List<D> findAllByIds(List<String> ids) throws QorvaException;

    /**
     * Update one record by ID.
     *
     * @param id    The ID of the record to update.
     * @param input The updated data of the record.
     * @return The updated record.
     * @throws QorvaException if the record is not found or an error occurs during update.
     */
    D updateOne(String id, D input) throws QorvaException;

    /**
     * Delete one record by ID.
     *
     * @param id The ID of the record to delete.
     * @param tenantId The Tenant of the document
     * @throws QorvaException if the record is not found or an error occurs during deletion.
     */
    void deleteOneById(String id, String tenantId) throws QorvaException;

    /**
     * Check if a record exists by its data.
     *
     * @param requestData The data of the record to check.
     * @return True if the record exists, false otherwise.
     * @throws QorvaException if an error occurs during the check.
     */

    boolean existsByData(D requestData) throws QorvaException;

    /**
     * Count all records.
     *
     * @param tenantId The Tenant of the document
     * @return The number of records.
     */
    long countAll(String tenantId) throws QorvaException;
}
