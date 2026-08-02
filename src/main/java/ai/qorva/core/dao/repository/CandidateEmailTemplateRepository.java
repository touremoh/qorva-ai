package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CandidateEmailTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateEmailTemplateRepository extends MongoRepository<CandidateEmailTemplate, String> {

	List<CandidateEmailTemplate> findByTenantIdOrderByNameAsc(String tenantId);

	Optional<CandidateEmailTemplate> findByIdAndTenantId(String id, String tenantId);

	boolean existsByTenantIdAndName(String tenantId, String name);

	long countByTenantId(String tenantId);
}
