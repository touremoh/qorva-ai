package ai.qorva.core.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** The signed-in user as Spring Security sees it, plus the user's tenant and credential version. */
public class QorvaUserDetails extends User {

	private final String tenantId;
	private final int credentialVersion;

	public QorvaUserDetails(String username, String password, boolean enabled, boolean accountNonExpired,
	                        boolean accountNonLocked, Collection<? extends GrantedAuthority> authorities, String tenantId,
	                        int credentialVersion) {
		super(username, password, enabled, accountNonExpired, true, accountNonLocked, authorities);
		this.tenantId = tenantId;
		this.credentialVersion = credentialVersion;
	}

	/** Bumped on every password change; access tokens carry the version they were issued for. */
	public int getCredentialVersion() {
		return credentialVersion;
	}

	public String getTenantId() {
		return tenantId;
	}
}
