package ai.qorva.core.admin.security;

import org.springframework.security.core.context.SecurityContextHolder;

/** The admin behind the current request. Only meaningful on {@code /admin/**} routes behind the admin chain. */
public final class AdminContext {

	private AdminContext() {
	}

	public static AdminPrincipal current() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof AdminPrincipal principal) {
			return principal;
		}
		throw new IllegalStateException("No admin in the security context");
	}

	/** What admin-made changes record as their author ({@code createdBy}, {@code statusChangedBy}, …). */
	public static String actor() {
		return "admin:" + current().email();
	}
}
