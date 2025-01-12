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
     * @param companyId The ID of the company that owns the data
     * @param input The data of the record.
     * @return The record.
     * @throws QorvaException if the record is not found.
     */
    D findOneByData(String companyId, D input) throws QorvaException;

    /**
     * Create one record.
     *
     * @param input The data of the record to create.
     * @return The created record.
     * @throws QorvaException if an error occurs during creation.
     */
    D createOne(D input) throws QorvaException;

    /**
     * Find many records with pagination.
     *
     * @param pageNumber The page number.
     * @param pageSize   The size of the page.
     * @return A pageable list of records.
     * @throws QorvaException if an error occurs during retrieval.
     */
    Page<D> findMany(int pageNumber, int pageSize) throws QorvaException;

    /**
     * Find many records by their IDs.
     *
     * @param ids The list of IDs.
     * @return A pageable list of records.
     * @throws QorvaException if an error occurs during retrieval.
     */
    Page<D> findManyByIds(List<String> ids) throws QorvaException;

    /**
     * Find many records by criteria
     *
     * @param pageNumber The page to retrieve
     * @param pageSize The size of the page to retrieve
     * @param searchTerms Criteria of the search
     * @return A pageable list of records.
     * @throws QorvaException if an error occurs during retrieval.
     */
    Page<D> findMany(int pageNumber, int pageSize, String searchTerms) throws QorvaException;

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
     * @throws QorvaException if the record is not found or an error occurs during deletion.
     */
    void deleteOneById(String id) throws QorvaException;

    /**
     * Check if a record exists by its data.
     *
     * @param input The data of the record to check.
     * @return True if the record exists, false otherwise.
     * @throws QorvaException if an error occurs during the check.
     */
    boolean existsByData(String companyId, D input) throws QorvaException;
}
