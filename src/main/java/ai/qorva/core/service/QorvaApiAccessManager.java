package ai.qorva.core.service;

import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;

import java.util.Set;

import static ai.qorva.core.enums.UserPermissionEnum.ALLOWED;

/**
 * Evaluates action-level and subscription-level permissions from the Spring Security context.
 * Authorities are loaded into the SecurityContext by JwtRequestFilter using the
 * "ACTION:PERMISSION" format (e.g. "CREATE_CV:ALLOWED"), so no additional DB call is needed.
 */
@Slf4j
@Service("accessManager")
public class QorvaApiAccessManager {

	private static final Set<String> ACTIVE_SUBSCRIPTION_STATUSES = Set.of(
		SubscriptionStatus.ACTIVE.getValue(),
		SubscriptionStatus.TRIALING.getValue()
	);

	private final TenantService tenantService;

	@Autowired
	public QorvaApiAccessManager(TenantService tenantService) {
		this.tenantService = tenantService;
	}

	public boolean hasAuthority(@AuthenticationPrincipal Authentication authentication, String action) {
		if (authentication == null || authentication.getAuthorities() == null) {
			return false;
		}
		String expected = action + ":" + ALLOWED.getValue();
		return authentication.getAuthorities().stream()
			.anyMatch(a -> a.getAuthority().equals(expected));
	}

	/** Step 11: grants access only if the tenant's subscription is active or trialing. */
	public boolean hasActiveSubscription(String tenantId) {
		if (tenantId == null) {
			return false;
		}
		try {
			var tenant = tenantService.findOneById(tenantId);
			var status = tenant.getSubscriptionInfo() != null
				? tenant.getSubscriptionInfo().getSubscriptionStatus()
				: null;
			return ACTIVE_SUBSCRIPTION_STATUSES.contains(status);
		} catch (QorvaException e) {
			log.warn("Could not verify subscription status for tenantId={}", tenantId, e);
			return false;
		}
	}
}
