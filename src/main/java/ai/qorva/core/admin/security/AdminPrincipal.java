package ai.qorva.core.admin.security;

import java.security.Principal;

/**
 * The signed-in admin of a request, as the admin JWT filter established it. Its name ({@code admin:<email>}) is what
 * Mongo auditing writes as {@code createdBy}/{@code lastUpdatedBy} on documents an admin creates.
 */
public record AdminPrincipal(String id, String email, String role) implements Principal {

	public boolean isOwner() {
		return ai.qorva.core.dao.entity.PlatformAdmin.ROLE_OWNER.equals(role);
	}

	@Override
	public String getName() {
		return "admin:" + email;
	}
}
