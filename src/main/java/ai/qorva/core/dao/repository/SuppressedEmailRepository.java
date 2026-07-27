package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.SuppressedEmail;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SuppressedEmailRepository extends MongoRepository<SuppressedEmail, String> {

	boolean existsByTenantIdAndEmail(String tenantId, String email);
}
