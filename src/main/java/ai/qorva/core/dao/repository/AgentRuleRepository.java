package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.AgentRule;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRuleRepository extends QorvaRepository<AgentRule> {

	List<AgentRule> findByTenantIdOrderByCreatedAtAsc(String tenantId);

	List<AgentRule> findByTenantIdAndOwnerEmailOrderByCreatedAtAsc(String tenantId, String ownerEmail);

	long countByTenantId(String tenantId);

	/** Tenant-wide purge only (CascadeRegistry). */
	long deleteByTenantId(String tenantId);
}
