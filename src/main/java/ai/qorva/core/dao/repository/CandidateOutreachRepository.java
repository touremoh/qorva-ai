package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CandidateOutreach;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface CandidateOutreachRepository extends MongoRepository<CandidateOutreach, String> {

	List<CandidateOutreach> findByTenantIdAndCvIdOrderByCreatedAtDesc(String tenantId, String cvId, Pageable pageable);

	List<CandidateOutreach> findByTenantIdAndCvId(String tenantId, String cvId);

	long deleteByTenantIdAndCvId(String tenantId, String cvId);

	long deleteByTenantIdAndCvIdIn(String tenantId, Collection<String> cvIds);

	long deleteByTenantId(String tenantId);
}
