package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.BackgroundJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BackgroundJobRepository extends MongoRepository<BackgroundJob, String> {

	List<BackgroundJob> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

	Optional<BackgroundJob> findByIdAndTenantId(String id, String tenantId);

	boolean existsByTenantIdAndTypeAndStatusIn(String tenantId, String type, List<String> statuses);
}
