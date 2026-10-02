package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.AgentRuleFiring;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentRuleFiringRepository extends QorvaRepository<AgentRuleFiring> {

	long deleteByTenantIdAndRuleId(String tenantId, String ruleId);

	/** Tenant-wide purge only (CascadeRegistry). */
	long deleteByTenantId(String tenantId);
}
