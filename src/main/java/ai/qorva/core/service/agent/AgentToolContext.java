package ai.qorva.core.service.agent;

import org.springframework.security.core.Authentication;

/** Who a tool acts for. Built by the runner inside the run's execution scope. */
public record AgentToolContext(String tenantId, String userEmail, String language, Authentication authentication) {
}
