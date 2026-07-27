package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.QualityIssueState;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface QualityIssueStateRepository extends MongoRepository<QualityIssueState, String> {

	List<QualityIssueState> findByTenantId(String tenantId);

	Optional<QualityIssueState> findByTenantIdAndIssueKey(String tenantId, String issueKey);

	long deleteByTenantIdAndIssueKey(String tenantId, String issueKey);
}
