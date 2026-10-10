package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.PlatformAdmin;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PlatformAdminRepository extends MongoRepository<PlatformAdmin, String> {

	PlatformAdmin findByEmail(String email);

	List<PlatformAdmin> findAllByOrderByCreatedAtAsc();

	long countByRoleAndStatus(String role, String status);
}
