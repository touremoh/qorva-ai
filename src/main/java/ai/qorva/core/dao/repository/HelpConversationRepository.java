package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.HelpConversation;
import org.springframework.stereotype.Repository;

@Repository
public interface HelpConversationRepository extends QorvaRepository<HelpConversation> {

	/** Tenant-wide purge only (CascadeRegistry). */
	long deleteByTenantId(String tenantId);
}
