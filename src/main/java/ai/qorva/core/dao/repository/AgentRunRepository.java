package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.AgentRun;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentRunRepository extends QorvaRepository<AgentRun> {

	/** Tenant-wide purge only (CascadeRegistry). */
	long deleteByTenantId(String tenantId);
}
