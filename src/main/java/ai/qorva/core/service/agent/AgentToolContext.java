package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;
import org.springframework.security.core.Authentication;

/** Who a tool acts for, and in which run. Built by the runner inside the run's execution scope. */
public record AgentToolContext(String tenantId, String userEmail, String language, Authentication authentication, String runId,
                               String origin, String timeZone) {

	public AgentToolContext(String tenantId, String userEmail, String language, Authentication authentication, String runId) {
		this(tenantId, userEmail, language, authentication, runId, AgentRun.ORIGIN_CHAT, null);
	}

	public AgentToolContext(String tenantId, String userEmail, String language, Authentication authentication) {
		this(tenantId, userEmail, language, authentication, null);
	}
}
