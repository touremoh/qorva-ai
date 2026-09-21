package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.OauthStateSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth2 authorization-code flow for Greenhouse, Zoho Recruit and Lever. The state parameter is
 * self-contained and HMAC-signed with the credentials key (tenant id + provider +
 * expiry) — the public callback trusts nothing else. Tokens are stored encrypted on
 * the connection; ensureFreshToken refreshes proactively before every sync run.
 */
@Slf4j
@Service
public class AtsOauthService {

	private static final long STATE_TTL_SECONDS = 600;
	private static final long REFRESH_SKEW_SECONDS = 120;

	private static final String GREENHOUSE_AUTHORIZE_URL = "https://api.greenhouse.io/oauth/authorize";
	private static final String GREENHOUSE_TOKEN_URL = "https://api.greenhouse.io/oauth/token";
	private static final String LEVER_TOKEN_URL = "https://auth.lever.co/oauth/token";

	/**
	 * Zoho accounts host per datacenter. A Zoho account lives in exactly one datacenter and
	 * is reachable nowhere else, so consent, token exchange, refresh and the API all have to
	 * happen on its host. Only keys from this map ever reach the HTTP client — the region a
	 * tenant picks is looked up here, never concatenated into a URL.
	 */
	private static final Map<String, String> ZOHO_ACCOUNTS_HOSTS = Map.of(
		"com", "https://accounts.zoho.com",
		"eu", "https://accounts.zoho.eu",
		"in", "https://accounts.zoho.in",
		"com.au", "https://accounts.zoho.com.au",
		"jp", "https://accounts.zoho.jp",
		"ca", "https://accounts.zohocloud.ca",
		"sa", "https://accounts.zoho.sa",
		"com.cn", "https://accounts.zoho.com.cn"
	);

	private static final String ZOHO_DEFAULT_DATACENTER = "com";

	/** Order the connect dialog lists datacenters in — by how often they are picked, not alphabetically. */
	private static final List<String> ZOHO_REGION_ORDER =
		List.of("com", "eu", "in", "com.au", "jp", "ca", "sa", "com.cn");

	/**
	 * Default scopes per provider — the minimum for the connector's calls (list candidates
	 * and jobs, read applications, write a note back). Greenhouse only grants what the app
	 * registration was approved for, so environments override via qorva.ats.oauth.*.scopes.
	 */
	private static final Map<AtsProviderEnum, String> DEFAULT_SCOPES = Map.of(
		AtsProviderEnum.GREENHOUSE, String.join(" ",
			"harvest:candidates:list",
			"harvest:candidates:get",
			"harvest:applications:list",
			"harvest:jobs:list",
			"harvest:job_posts:list",
			"harvest:candidates:notes:create"),
		AtsProviderEnum.ZOHO_RECRUIT, "ZohoRecruit.modules.ALL",
		AtsProviderEnum.LEVER, "opportunities:read:admin postings:read:admin notes:write:admin offline_access"
	);

	private final AtsProperties properties;
	private final AtsHttpClient http;
	private final CredentialsCipher cipher;
	private final AtsConnectionRepository connectionRepository;

	public AtsOauthService(AtsProperties properties, AtsHttpClient http, CredentialsCipher cipher,
		AtsConnectionRepository connectionRepository) {
		this.properties = properties;
		this.http = http;
		this.cipher = cipher;
		this.connectionRepository = connectionRepository;
	}

	/**
	 * Callback the provider redirects back to. Providers match this byte for byte against
	 * their app registration, so it is per provider and overridable in configuration.
	 */
	public String redirectUri(AtsProviderEnum provider) {
		var client = properties.getOauth().get(provider.getValue());
		if (client != null && client.getRedirectUri() != null && !client.getRedirectUri().isBlank()) {
			return client.getRedirectUri().trim();
		}
		return properties.getPublicBaseUrl() + "/integrations/" + provider.getValue() + "/oauth/callback";
	}

	private String scopes(AtsProviderEnum provider) {
		var client = properties.getOauth().get(provider.getValue());
		if (client != null && client.getScopes() != null && !client.getScopes().isBlank()) {
			return client.getScopes().trim();
		}
		return DEFAULT_SCOPES.getOrDefault(provider, "");
	}

	public String buildConsentUrl(AtsProviderEnum provider, String tenantId) throws QorvaException {
		return buildConsentUrl(provider, tenantId, null);
	}

	/**
	 * Consent URL for the tenant's own datacenter. Zoho accounts exist in exactly one region
	 * and its accounts host is the only one that will authenticate them; starting at the US
	 * host works only for a multi-DC app registration, and fails outright otherwise. The
	 * region rides in the signed state so the callback can exchange the code on the same host.
	 */
	public String buildConsentUrl(AtsProviderEnum provider, String tenantId, String region) throws QorvaException {
		var client = oauthClient(provider);
		var datacenter = provider == AtsProviderEnum.ZOHO_RECRUIT ? validRegion(region) : null;
		var state = signState(tenantId, provider, datacenter);
		var redirect = URLEncoder.encode(redirectUri(provider), StandardCharsets.UTF_8);
		var scope = URLEncoder.encode(scopes(provider), StandardCharsets.UTF_8);
		return switch (provider) {
			case GREENHOUSE -> GREENHOUSE_AUTHORIZE_URL
				+ "?client_id=" + client.getClientId()
				+ "&redirect_uri=" + redirect
				+ "&response_type=code"
				+ "&scope=" + scope
				+ "&state=" + state;
			case ZOHO_RECRUIT -> zohoAccountsHost(datacenter) + "/oauth/v2/auth"
				+ "?scope=" + scope
				+ "&client_id=" + client.getClientId()
				+ "&response_type=code&access_type=offline&prompt=consent"
				+ "&redirect_uri=" + redirect
				+ "&state=" + state;
			case LEVER -> "https://auth.lever.co/authorize"
				+ "?client_id=" + client.getClientId()
				+ "&redirect_uri=" + redirect
				+ "&response_type=code"
				+ "&scope=" + scope
				+ "&audience=" + URLEncoder.encode("https://api.lever.co/v1/", StandardCharsets.UTF_8)
				+ "&prompt=consent"
				+ "&state=" + state;
			default -> throw new QorvaException(QorvaErrorCodes.ATS_PROVIDER_UNKNOWN,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		};
	}

	/**
	 * Validated state → (tenantId, provider, datacenter). Throws on tamper or expiry.
	 * datacenter is null for providers that have only one region, and for states signed
	 * before regions were carried.
	 */
	public record StateClaims(String tenantId, AtsProviderEnum provider, String datacenter) {}

	public StateClaims validateState(String state) throws QorvaException {
		try {
			var parts = stateSigner().verify(state);
			if (Instant.now().getEpochSecond() > Long.parseLong(parts[2])) {
				throw new IllegalStateException("expired");
			}
			// parts[3] is the nonce; the datacenter was appended after it, so older states
			// (four parts) stay valid and simply carry no region.
			var datacenter = parts.length > 4 && !parts[4].isBlank() ? parts[4] : null;
			return new StateClaims(parts[0], AtsProviderEnum.fromValue(parts[1]), datacenter);
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.ATS_OAUTH_STATE_INVALID,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
	}

	public AtsCredentials exchangeCode(AtsProviderEnum provider, String code, String accountsServer) throws QorvaException {
		return exchangeCode(provider, code, accountsServer, null);
	}

	/**
	 * Exchange the authorization code for tokens. For Zoho the code is only redeemable on the
	 * datacenter that issued it: accountsServer is what Zoho itself echoed back, and the
	 * region from the signed state is the fallback when it does not (both are ignored by the
	 * other providers). The api_domain in the response is the authoritative API host for the
	 * grant, so it is stored rather than re-derived from the accounts hostname.
	 */
	public AtsCredentials exchangeCode(AtsProviderEnum provider, String code, String accountsServer,
		String stateDatacenter) throws QorvaException {
		var client = oauthClient(provider);
		var zohoHost = knownZohoHost(accountsServer) != null
			? knownZohoHost(accountsServer)
			: zohoAccountsHost(stateDatacenter);
		var tokenUrl = switch (provider) {
			case GREENHOUSE -> GREENHOUSE_TOKEN_URL;
			case LEVER -> LEVER_TOKEN_URL;
			case ZOHO_RECRUIT -> zohoHost + "/oauth/v2/token";
			default -> throw new QorvaException(QorvaErrorCodes.ATS_PROVIDER_UNKNOWN,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		};
		var body = http.postForm(provider, tokenUrl, Map.of(
			"grant_type", "authorization_code",
			"client_id", client.getClientId(),
			"client_secret", client.getClientSecret(),
			"redirect_uri", redirectUri(provider),
			"code", code));
		assertToken(body.path("access_token").asText(null));
		boolean zoho = provider == AtsProviderEnum.ZOHO_RECRUIT;
		return AtsCredentials.builder()
			.accessToken(body.path("access_token").asText())
			.refreshToken(body.path("refresh_token").asText(null))
			.tokenExpiresAt(Instant.now().plusSeconds(body.path("expires_in").asLong(3600)))
			.datacenter(zoho ? datacenterOf(zohoHost) : null)
			.apiDomain(zoho ? body.path("api_domain").asText(null) : null)
			.build();
	}

	/** The callback's accounts-server value, accepted only when it is a host we know. */
	private String knownZohoHost(String accountsServer) {
		if (accountsServer == null || accountsServer.isBlank()) {
			return null;
		}
		var normalized = accountsServer.trim().replaceAll("/+$", "");
		return ZOHO_ACCOUNTS_HOSTS.containsValue(normalized) ? normalized : null;
	}

	/**
	 * Refresh the access token when it is at or past expiry (with skew), persisting the
	 * re-encrypted credentials. Returns the fresh credentials to use for this run.
	 */
	public AtsCredentials ensureFreshToken(AtsConnection connection, AtsCredentials credentials) throws QorvaException {
		var provider = AtsProviderEnum.fromValue(connection.getProvider());
		// Greenhouse and Lever moved to credential forms, but connections made through their
		// partner redirect flows still hold refresh tokens and must keep being refreshed.
		if (provider.getAuthKind() != AtsProviderEnum.AuthKind.OAUTH2
			&& !StringUtils.hasText(credentials.getRefreshToken())) {
			return credentials;
		}
		var expiry = credentials.getTokenExpiresAt();
		if (expiry != null && Instant.now().plusSeconds(REFRESH_SKEW_SECONDS).isBefore(expiry)) {
			return credentials;
		}
		if (credentials.getRefreshToken() == null) {
			throw new QorvaException(QorvaErrorCodes.ATS_AUTH_FAILED,
				HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
		}
		var client = oauthClient(provider);
		var tokenUrl = switch (provider) {
			case GREENHOUSE -> GREENHOUSE_TOKEN_URL;
			case LEVER -> LEVER_TOKEN_URL;
			case ZOHO_RECRUIT -> zohoAccountsHost(credentials.getDatacenter()) + "/oauth/v2/token";
			default -> throw new QorvaException(QorvaErrorCodes.ATS_PROVIDER_UNKNOWN,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		};
		var body = http.postForm(provider, tokenUrl, Map.of(
			"grant_type", "refresh_token",
			"client_id", client.getClientId(),
			"client_secret", client.getClientSecret(),
			"refresh_token", credentials.getRefreshToken()));
		assertToken(body.path("access_token").asText(null));
		credentials.setAccessToken(body.path("access_token").asText());
		if (body.path("refresh_token").asText(null) != null) {
			credentials.setRefreshToken(body.path("refresh_token").asText());
		}
		credentials.setTokenExpiresAt(Instant.now().plusSeconds(body.path("expires_in").asLong(3600)));
		// Zoho repeats api_domain on refresh; keep it current rather than trusting the value
		// captured at consent, and never overwrite a good one with a missing field.
		if (body.path("api_domain").asText(null) != null) {
			credentials.setApiDomain(body.path("api_domain").asText());
		}
		connection.setEncryptedCredentials(cipher.encrypt(credentials));
		connectionRepository.save(connection);
		return credentials;
	}

	private void assertToken(String accessToken) throws QorvaException {
		if (accessToken == null || accessToken.isBlank()) {
			throw new QorvaException(QorvaErrorCodes.ATS_AUTH_FAILED,
				HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
		}
	}

	/**
	 * Reverse of the host registry: which datacenter an accounts host belongs to. Matching
	 * against known hosts rather than parsing the domain keeps Canada (accounts.zohocloud.ca)
	 * from being read as the US default, which would send every later call to the wrong region.
	 */
	private String datacenterOf(String accountsHost) {
		if (accountsHost == null) {
			return ZOHO_DEFAULT_DATACENTER;
		}
		var normalized = accountsHost.replaceAll("/+$", "");
		return ZOHO_ACCOUNTS_HOSTS.entrySet().stream()
			.filter(entry -> normalized.equalsIgnoreCase(entry.getValue()))
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(ZOHO_DEFAULT_DATACENTER);
	}

	/** Accounts host for a datacenter key, defaulting to the US host. */
	private String zohoAccountsHost(String datacenter) {
		return ZOHO_ACCOUNTS_HOSTS.getOrDefault(
			datacenter != null ? datacenter : ZOHO_DEFAULT_DATACENTER,
			ZOHO_ACCOUNTS_HOSTS.get(ZOHO_DEFAULT_DATACENTER));
	}

	/**
	 * Validates a tenant-chosen region against the registry. Blank means "not specified" and
	 * falls back to the US host; an unrecognised value is a bad request rather than a silent
	 * redirect to the wrong datacenter.
	 */
	private String validRegion(String region) throws QorvaException {
		if (region == null || region.isBlank()) {
			return ZOHO_DEFAULT_DATACENTER;
		}
		var normalized = region.trim().toLowerCase();
		if (!ZOHO_ACCOUNTS_HOSTS.containsKey(normalized)) {
			throw new QorvaException(QorvaErrorCodes.HTTP_VALIDATION,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		return normalized;
	}

	/** Datacenter keys the connect UI may offer, most commonly used first. */
	public static List<String> zohoRegions() {
		return ZOHO_REGION_ORDER;
	}

	private AtsProperties.OauthClient oauthClient(AtsProviderEnum provider) throws QorvaException {
		var client = properties.getOauth().get(provider.getValue());
		if (client == null || client.getClientId() == null || client.getClientId().isBlank()) {
			throw new QorvaException(QorvaErrorCodes.ATS_OAUTH_NOT_CONFIGURED,
				HttpStatus.NOT_IMPLEMENTED.value(), HttpStatus.NOT_IMPLEMENTED);
		}
		return client;
	}

	private String signState(String tenantId, AtsProviderEnum provider, String datacenter) {
		return stateSigner().sign(List.of(tenantId, provider.getValue(),
			String.valueOf(Instant.now().getEpochSecond() + STATE_TTL_SECONDS), UUID.randomUUID().toString(),
			datacenter != null ? datacenter : ""));
	}

	/** Same key as the credentials cipher, as before the signer was shared with the mailbox flow. */
	private OauthStateSigner stateSigner() {
		return new OauthStateSigner(properties.getCredentialsKey());
	}
}
