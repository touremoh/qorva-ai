package ai.qorva.core.admin.controller;

/** {@code @PreAuthorize} expressions for admin routes; every admin route already requires a signed-in admin. */
public final class AdminRoles {

	private AdminRoles() {
	}

	public static final String OWNER = "hasAuthority('ADMIN_ROLE:OWNER')";
}
