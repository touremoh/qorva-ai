package ai.qorva.core.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;

import static ai.qorva.core.enums.UserPermissionEnum.ALLOWED;

/**
 * Evaluates action-level permissions directly from the Spring Security context.
 * Authorities are loaded into the SecurityContext by JwtRequestFilter using the
 * "ACTION:PERMISSION" format (e.g. "CREATE_CV:ALLOWED"), so no additional DB call is needed.
 */
@Service("accessManager")
public class QorvaApiAccessManager {

	public boolean hasAuthority(@AuthenticationPrincipal Authentication authentication, String action) {
		if (authentication == null || authentication.getAuthorities() == null) {
			return false;
		}
		String expected = action + ":" + ALLOWED.getValue();
		return authentication.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals(expected));
	}
}
