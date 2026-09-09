package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mints Greenhouse Harvest v3 bearer tokens from a tenant's own client id and secret.
 *
 * <p>Harvest v3 uses the OAuth client-credentials grant: the customer creates the
 * credentials themselves in Dev Center → API Credential Management as an "Unlisted vendor",
 * so there is no partner approval, no consent screen and no refresh token. That makes this
 * a credentials-form provider from the UI's point of view even though the wire protocol is
 * OAuth, which is why it lives here rather than in {@link AtsOauthService}.</p>
 *
 * <p>Tokens are short-lived and cached in memory for the process, keyed by the credential
 * pair so two tenants never share one. Nothing is persisted: re-minting costs a single
 * request and avoids writing a second copy of a secret-derived token to the database.</p>
 */
@Component
public class GreenhouseTokenService {

	/** Re-mint this far ahead of expiry so a token cannot die mid-request. */
	private static final long EXPIRY_SKEW_SECONDS = 60;

	private final AtsHttpClient http;
	private final AtsProperties properties;
	private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

	public GreenhouseTokenService(AtsHttpClient http, AtsProperties properties) {
		this.http = http;
		this.properties = properties;
	}

	private record CachedToken(String accessToken, Instant expiresAt) {
		boolean usable() {
			return expiresAt != null && Instant.now().plusSeconds(EXPIRY_SKEW_SECONDS).isBefore(expiresAt);
		}
	}

	public boolean canMint(AtsCredentials credentials) {
		return credentials != null
			&& StringUtils.hasText(credentials.getClientId())
			&& StringUtils.hasText(credentials.getClientSecret());
	}

	/** Cached bearer token for these credentials, minting a fresh one when needed. */
	public String accessToken(AtsCredentials credentials) throws QorvaException {
		if (!canMint(credentials)) {
			throw new QorvaException(QorvaErrorCodes.ATS_AUTH_FAILED,
				HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
		}
		var key = cacheKey(credentials);
		var cached = cache.get(key);
		if (cached != null && cached.usable()) {
			return cached.accessToken();
		}
		var minted = mint(credentials);
		cache.put(key, minted);
		return minted.accessToken();
	}

	/** Drops any cached token for these credentials — used when Greenhouse rejects one. */
	public void invalidate(AtsCredentials credentials) {
		if (canMint(credentials)) {
			cache.remove(cacheKey(credentials));
		}
	}

	private CachedToken mint(AtsCredentials credentials) throws QorvaException {
		var basic = Base64.getEncoder().encodeToString(
			(credentials.getClientId() + ":" + credentials.getClientSecret()).getBytes(StandardCharsets.UTF_8));

		// `sub` scopes the token to a Greenhouse user. Harvest needs one to attribute the
		// notes Qorva writes back; without it the token acts as the credential's own user.
		var form = StringUtils.hasText(credentials.getOnBehalfOfUserId())
			? Map.of("grant_type", "client_credentials", "sub", credentials.getOnBehalfOfUserId())
			: Map.of("grant_type", "client_credentials");

		var body = http.postForm(AtsProviderEnum.GREENHOUSE, properties.getGreenhouseTokenUrl(),
			Map.of("Authorization", "Basic " + basic), form);

		var accessToken = body.path("access_token").asText(null);
		if (!StringUtils.hasText(accessToken)) {
			throw new QorvaException(QorvaErrorCodes.ATS_AUTH_FAILED,
				HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
		}
		return new CachedToken(accessToken, Instant.now().plusSeconds(body.path("expires_in").asLong(3600)));
	}

	/**
	 * Keyed on the secret as well as the id so a rotated secret cannot serve a token minted
	 * from the old one. The pair is hashed rather than held as a map key in clear.
	 */
	private String cacheKey(AtsCredentials credentials) {
		return Integer.toHexString((credentials.getClientId() + ':' + credentials.getClientSecret()).hashCode())
			+ ':' + credentials.getClientId();
	}
}
