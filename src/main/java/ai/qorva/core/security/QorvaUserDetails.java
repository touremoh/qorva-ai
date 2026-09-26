package ai.qorva.core.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** The signed-in user as Spring Security sees it, plus the tenant the user belongs to. */
public class QorvaUserDetails extends User {

	private final String tenantId;

	public QorvaUserDetails(String username, String password, boolean enabled, boolean accountNonExpired,
	                        boolean accountNonLocked, Collection<? extends GrantedAuthority> authorities, String tenantId) {
		super(username, password, enabled, accountNonExpired, true, accountNonLocked, authorities);
		this.tenantId = tenantId;
	}

	public String getTenantId() {
		return tenantId;
	}
}
