package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.BackgroundJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BackgroundJobRepository extends MongoRepository<BackgroundJob, String>, OwnedLookup<BackgroundJob> {

	List<BackgroundJob> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

	List<BackgroundJob> findByTenantIdAndTypeOrderByCreatedAtDesc(String tenantId, String type, Pageable pageable);

	boolean existsByTenantIdAndTypeAndStatusIn(String tenantId, String type, List<String> statuses);

	boolean existsByConnectionIdAndStatusIn(String connectionId, List<String> statuses);

	List<BackgroundJob> findByTenantIdAndConnectionIdOrderByCreatedAtDesc(
		String tenantId, String connectionId, Pageable pageable);
}
