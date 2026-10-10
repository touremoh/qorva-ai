package ai.qorva.core.admin.security;

import ai.qorva.core.admin.config.AdminProperties;
import ai.qorva.core.dao.entity.PlatformAdmin;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Admin tokens: signed with the admin key (never the tenant one), {@code typ=admin}, subject = admin id,
 * {@code cv} = the admin's credential version. Set-password links use the same key with {@code typ=admin_set_password}.
 * A tenant token fails the signature here, and an admin token fails it in the tenant filter.
 */
@Component
public class AdminTokens {

	public static final String TYPE = "typ";
	public static final String TYPE_ADMIN = "admin";
	public static final String TYPE_SET_PASSWORD = "admin_set_password";
	public static final String CREDENTIAL_VERSION = "cv";
	public static final String ROLE = "role";

	private final AdminProperties properties;

	public AdminTokens(AdminProperties properties) {
		this.properties = properties;
	}

	public record Issued(String token, Instant expiresAt) {}

	public Issued session(PlatformAdmin admin) {
		var expiresAt = Instant.now().plus(properties.getSessionTtl());
		return new Issued(sign(admin, TYPE_ADMIN, expiresAt), expiresAt);
	}

	public String setPasswordLink(PlatformAdmin admin) {
		return sign(admin, TYPE_SET_PASSWORD, Instant.now().plus(properties.getSetPasswordTtl()));
	}

	/** Claims of a valid, unexpired token of the given type; empty for anything else. */
	public Optional<Claims> parse(String token, String expectedType) {
		try {
			var claims = Jwts.parser().verifyWith(properties.secretKey()).build().parseSignedClaims(token).getPayload();
			return expectedType.equals(claims.get(TYPE, String.class)) ? Optional.of(claims) : Optional.empty();
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public static int credentialVersion(Claims claims) {
		var cv = claims.get(CREDENTIAL_VERSION, Integer.class);
		return cv != null ? cv : -1;
	}

	private String sign(PlatformAdmin admin, String type, Instant expiresAt) {
		return Jwts.builder()
			.subject(admin.getId())
			.claim(TYPE, type)
			.claim(CREDENTIAL_VERSION, admin.getCredentialVersion())
			.claim(ROLE, admin.getRole())
			.issuedAt(new Date())
			.expiration(Date.from(expiresAt))
			.signWith(properties.secretKey())
			.compact();
	}
}
