package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.AtsConnection;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AtsConnectionRepository extends MongoRepository<AtsConnection, String> {

	List<AtsConnection> findByTenantIdOrderByCreatedAtAsc(String tenantId);

	Optional<AtsConnection> findByIdAndTenantId(String id, String tenantId);

	boolean existsByTenantIdAndProvider(String tenantId, String provider);

	long countByTenantId(String tenantId);

	List<AtsConnection> findByStatus(String status);
}
