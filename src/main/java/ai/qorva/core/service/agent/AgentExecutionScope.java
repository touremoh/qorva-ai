package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.security.LanguageContextHolder;
import ai.qorva.core.security.QorvaUserDetails;
import ai.qorva.core.security.SubscriptionGate;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.QorvaApiAccessManager;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.service.TenantService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Runs agent work as the run's user: inside the run's tenant (TenantScope), with a SecurityContext
 * built from fresh authorities (so auditing records the user and permission checks see today's
 * rights), and in the run's language. Re-applies what the request filter would have checked, since
 * the worker has no request: the account is usable, still holds USE_AGENT, and the subscription is
 * not blocked. Everything is cleared afterwards, including on failure.
 */
@Component
public class AgentExecutionScope {

	@FunctionalInterface
	public interface AgentWork<T> {
		T call(AgentToolContext ctx) throws Exception;
	}

	private final QorvaUserDetailsService userDetailsService;
	private final TenantService tenantService;
	private final QorvaApiAccessManager accessManager;

	public AgentExecutionScope(QorvaUserDetailsService userDetailsService, TenantService tenantService,
	                           QorvaApiAccessManager accessManager) {
		this.userDetailsService = userDetailsService;
		this.tenantService = tenantService;
		this.accessManager = accessManager;
	}

	public <T> T call(AgentRun run, AgentWork<T> work) throws Exception {
		return TenantScope.callAs(run.getTenantId(), () -> {
			var authentication = authenticate(run);
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
			LanguageContextHolder.setLanguage(run.getLanguage());
			try {
				assertSubscriptionActive(run);
				return work.call(new AgentToolContext(run.getTenantId(), run.getUserEmail(), run.getLanguage(), authentication, run.getId()));
			} finally {
				SecurityContextHolder.clearContext();
				LanguageContextHolder.clear();
			}
		});
	}

	private UsernamePasswordAuthenticationToken authenticate(AgentRun run) {
		QorvaUserDetails user;
		try {
			user = (QorvaUserDetails) userDetailsService.loadUserByUsername(run.getUserEmail());
		} catch (UsernameNotFoundException e) {
			throw new AgentPreconditionException(QorvaErrorCodes.AGENT_USER_UNAVAILABLE, "user no longer exists");
		}
		if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()
			|| !run.getTenantId().equals(user.getTenantId())) {
			throw new AgentPreconditionException(QorvaErrorCodes.AGENT_USER_UNAVAILABLE, "user account not usable");
		}
		var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
		authentication.setDetails(run.getTenantId());
		if (!accessManager.hasPermission(authentication, UserActionsEnum.USE_AGENT.getValue())) {
			throw new AgentPreconditionException(QorvaErrorCodes.AGENT_USER_UNAVAILABLE, "user lost USE_AGENT");
		}
		return authentication;
	}

	private void assertSubscriptionActive(AgentRun run) throws Exception {
		var tenant = tenantService.findOneById(run.getTenantId());
		var status = tenant.getSubscriptionInfo() != null ? tenant.getSubscriptionInfo().getSubscriptionStatus() : null;
		if (SubscriptionGate.blocks(status)) {
			throw new AgentPreconditionException(QorvaErrorCodes.AUTH_SUBSCRIPTION_INACTIVE, "subscription " + status);
		}
	}
}
