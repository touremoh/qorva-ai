package ai.qorva.core.security;

import ai.qorva.core.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Objects;

/**
 * When a signed, unexpired token may act for its user. Used by the request filter and by token
 * refresh, so a token the API would refuse can never be exchanged for a fresh one.
 */
public final class AccessTokenPolicy {

	private AccessTokenPolicy() {
	}

	/**
	 * Only access tokens authenticate. Single-purpose tokens (set-password links) carry a
	 * {@code purpose}; access tokens carry {@code typ=access}. Tokens minted before that claim
	 * existed have neither and stay valid until they expire.
	 */
	public static boolean isAccessToken(Claims claims) {
		if (claims.get(JwtUtils.PURPOSE) != null) {
			return false;
		}
		var type = claims.get(JwtUtils.TYPE, String.class);
		return type == null || JwtUtils.TYPE_ACCESS.equals(type);
	}

	/** The token may act for this (freshly loaded) user. */
	public static boolean accepts(Claims claims, UserDetails user) {
		return isAccessToken(claims)
			&& isUsable(user)
			&& belongsToTenant(user, claims.get(JwtUtils.TENANT_ID, String.class))
			&& isCurrentCredential(claims, user);
	}

	/** A deactivated, locked or deleted account stops working at once, not when its token expires. */
	private static boolean isUsable(UserDetails user) {
		return user.isEnabled() && user.isAccountNonLocked() && user.isAccountNonExpired();
	}

	/** The tenant a token names must still be the user's own. */
	private static boolean belongsToTenant(UserDetails user, String tenantId) {
		return !(user instanceof QorvaUserDetails qorvaUser) || Objects.equals(qorvaUser.getTenantId(), tenantId);
	}

	/**
	 * A password change bumps the user's credential version, which ends every session opened with
	 * the old password. Tokens minted before the version claim existed carry none and stay valid
	 * until they expire, so the deploy itself signs nobody out.
	 */
	private static boolean isCurrentCredential(Claims claims, UserDetails user) {
		var version = claims.get(JwtUtils.CREDENTIAL_VERSION, Integer.class);
		return version == null || !(user instanceof QorvaUserDetails qorvaUser) || version == qorvaUser.getCredentialVersion();
	}
}
