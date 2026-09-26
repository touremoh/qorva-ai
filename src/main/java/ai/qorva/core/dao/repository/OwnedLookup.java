package ai.qorva.core.dao.repository;

import java.util.Optional;

/**
 * Lookup of one document by id within one tenant — the tenant is part of the query, so another
 * tenant's document and a missing one are indistinguishable. Implemented for every repository by
 * {@link QorvaMongoRepositoryImpl}; a repository interface opts in by extending this.
 */
public interface OwnedLookup<E> {

	Optional<E> findByIdInTenant(String id, String tenantId);
}
