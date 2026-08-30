package ai.qorva.core.utils;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.JwtDTO;
import ai.qorva.core.dto.TenantDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@UtilityClass
public class JwtUtils {

	String TENANT_ID = "tenantId";
	String SUBSCRIPTION_PLAN = "subscriptionPlan";
	String SUBSCRIPTION_STATUS = "subscriptionStatus";

	public String PURPOSE = "purpose";
	public String CREDENTIAL_VERSION = "cv";
	public String PURPOSE_SET_PASSWORD = "SET_PASSWORD";

	public String extractToken(String bearerToken) {
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}

	public String extractUsername(String token, SecretKey jwtSecret) {
		return extractClaim(token, Claims::getSubject, jwtSecret);
	}

	public String extractTenantId(String token, SecretKey jwtSecret) {
		return extractClaim(token, claims -> claims.get(TENANT_ID, String.class), jwtSecret);
	}

	public String extractSubscriptionPlan(String token, SecretKey jwtSecret) {
		return extractClaim(token, claims -> claims.get(SUBSCRIPTION_PLAN, String.class), jwtSecret);
	}

	public Date extractExpiration(String token, SecretKey jwtSecret) {
		return extractClaim(token, Claims::getExpiration, jwtSecret);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver, SecretKey jwtSecret) {
		final Claims claims = extractAllClaims(token, jwtSecret);
		return claimsResolver.apply(claims);
	}

	public Claims extractAllClaims(String token, SecretKey jwtSecret) {
		return Jwts.parser().verifyWith(jwtSecret).build().parseSignedClaims(token).getPayload();
	}

	public Boolean isTokenExpired(String token, SecretKey jwtSecret) {
		return extractExpiration(token, jwtSecret).before(new Date());
	}

	public String generateToken(UserDetails userDetails, JwtConfig jwtConfig, TenantDTO tenantDTO) {
		Map<String, Object> claims = new HashMap<>();
		claims.put(TENANT_ID, tenantDTO.getId());

		var subscriptionInfo = tenantDTO.getSubscriptionInfo();
		if (Objects.nonNull(subscriptionInfo) && StringUtils.hasText(subscriptionInfo.getSubscriptionPlan())) {
			claims.put(SUBSCRIPTION_PLAN, subscriptionInfo.getSubscriptionPlan());
			claims.put(SUBSCRIPTION_STATUS, subscriptionInfo.getSubscriptionStatus());
		}
		return createToken(claims, userDetails.getUsername(), jwtConfig);
	}

	private String createToken(Map<String, Object> claims, String subject, JwtConfig jwtConfig) {
		// signWith(key) picks the strongest HMAC alg the key supports (>=64 bytes -> HS512),
		// so existing secrets keep working; jjwt 0.12 rejects keys under 32 bytes outright.
		return Jwts.builder()
			.claims(claims)
			.subject(subject)
			.issuedAt(new Date(System.currentTimeMillis()))
			.expiration(new Date(System.currentTimeMillis() + jwtConfig.getTimeToLiveInMillis()))
			.signWith(jwtConfig.getSecretKey())
			.compact();
	}

	public Boolean isTokenValid(String token, UserDetails userDetails, SecretKey jwtSecret) {
		final String username = extractUsername(token, jwtSecret);
		return (username.equals(userDetails.getUsername()) && !isTokenExpired(token, jwtSecret));
	}

	/**
	 * Mints a single-purpose, time-boxed set-password token. The subject is the user id and the
	 * {@code cv} claim pins the user's current credential version so the token becomes invalid once
	 * the password is changed (single-use).
	 */
	public String generateSetPasswordToken(String userId, int credentialVersion, JwtConfig jwtConfig) {
		Map<String, Object> claims = new HashMap<>();
		claims.put(PURPOSE, PURPOSE_SET_PASSWORD);
		claims.put(CREDENTIAL_VERSION, credentialVersion);
		return Jwts.builder()
			.claims(claims)
			.subject(userId)
			.issuedAt(new Date(System.currentTimeMillis()))
			.expiration(new Date(System.currentTimeMillis() + jwtConfig.getSetPasswordTtlInMillis()))
			.signWith(jwtConfig.getSecretKey())
			.compact();
	}

	public JwtDTO generateAndBuildToken(UserDetails userDetails, JwtConfig jwtConfig, TenantDTO tenantDTO) {
		var accessToken = generateToken(userDetails, jwtConfig, tenantDTO);
		return JwtDTO.builder()
			.accessToken(accessToken)
			.expiresIn(extractExpiration(accessToken, jwtConfig.getSecretKey()).getTime())
			.tokenType("Bearer")
			.build();
	}
}
