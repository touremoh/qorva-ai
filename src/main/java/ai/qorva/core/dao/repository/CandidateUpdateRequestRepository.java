package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CandidateUpdateRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateUpdateRequestRepository extends MongoRepository<CandidateUpdateRequest, String> {

	Optional<CandidateUpdateRequest> findByTokenHash(String tokenHash);

	boolean existsByTenantIdAndCvIdAndStatusIn(String tenantId, String cvId, List<String> statuses);

	long deleteByTenantId(String tenantId);
}
