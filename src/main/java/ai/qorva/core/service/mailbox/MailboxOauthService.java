package ai.qorva.core.service.mailbox;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.config.MailboxProperties;
import ai.qorva.core.enums.MailboxProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.OauthStateSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delegated authorization-code flow for a recruiter's own mailbox. Mirrors the ATS flow
 * ({@code AtsOauthService}): the {@code state} is HMAC-signed and carries tenant, user, provider,
 * expiry and a nonce — the public callback trusts nothing else. Tokens are refreshed proactively
 * before every send; a refused refresh ({@code invalid_grant}) means the user must reconnect.
 */
@Slf4j
@Service
public class MailboxOauthService {

	private static final long STATE_TTL_SECONDS = 600;
	private static final long REFRESH_SKEW_SECONDS = 120;

	/** offline_access → refresh token; User.Read → /me for the mailbox address; Mail.Send → the point. */
	static final String MICROSOFT_SCOPES = "offline_access openid email User.Read Mail.Send";

	private final MailboxProperties properties;
	private final AtsProperties atsProperties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public MailboxOauthService(MailboxProperties properties, AtsProperties atsProperties,
	                           RestClient.Builder builder, ObjectMapper objectMapper) {
		this.properties = properties;
		this.atsProperties = atsProperties;
		this.restClient = builder.build();
		this.objectMapper = objectMapper;
	}

	public boolean isConfigured(MailboxProviderEnum provider) {
		return provider == MailboxProviderEnum.MICROSOFT && properties.getMicrosoft().isConfigured();
	}

	/** Registered byte for byte at the provider — reuses the API's public origin like ATS webhooks and OAuth do. */
	public String redirectUri(MailboxProviderEnum provider) {
		return atsProperties.getPublicBaseUrl() + "/integrations/mailbox/" + provider.name().toLowerCase() + "/oauth/callback";
	}

	public String buildConsentUrl(MailboxProviderEnum provider, String tenantId, String userId) throws QorvaException {
		assertConfigured(provider);
		var state = signState(tenantId, userId, provider);
		var redirect = URLEncoder.encode(redirectUri(provider), StandardCharsets.UTF_8);
		var scope = URLEncoder.encode(MICROSOFT_SCOPES, StandardCharsets.UTF_8);
		return authority() + "/oauth2/v2.0/authorize"
			+ "?client_id=" + properties.getMicrosoft().getClientId()
			+ "&response_type=code"
			+ "&redirect_uri=" + redirect
			+ "&response_mode=query"
			+ "&scope=" + scope
			+ "&prompt=select_account"
			+ "&state=" + state;
	}

	public record StateClaims(String tenantId, String userId, MailboxProviderEnum provider) {}

	public StateClaims validateState(String state) throws QorvaException {
		try {
			var parts = stateSigner().verify(state);
			if (Instant.now().getEpochSecond() > Long.parseLong(parts[3])) {
				throw new IllegalStateException("expired");
			}
			var provider = MailboxProviderEnum.fromValue(parts[2]);
			if (provider == null) {
				throw new IllegalStateException("unknown provider");
			}
			return new StateClaims(parts[0], parts[1], provider);
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.MAILBOX_OAUTH_STATE_INVALID,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
	}

	public MailboxTokens exchangeCode(MailboxProviderEnum provider, String code) throws QorvaException {
		assertConfigured(provider);
		var body = postForm(tokenUrl(), Map.of(
			"grant_type", "authorization_code",
			"client_id", properties.getMicrosoft().getClientId(),
			"client_secret", properties.getMicrosoft().getClientSecret(),
			"redirect_uri", redirectUri(provider),
			"scope", MICROSOFT_SCOPES,
			"code", code));
		return tokensFrom(body, null);
	}

	/**
	 * Fresh tokens for a send: the stored ones when they still have more than the skew left,
	 * otherwise refreshed. The caller persists the returned tokens when {@code changed()} is true.
	 */
	public record Refreshed(MailboxTokens tokens, boolean changed) {}

	public Refreshed ensureFreshToken(MailboxProviderEnum provider, MailboxTokens tokens) throws QorvaException {
		var expiry = tokens.getExpiresAt();
		if (expiry != null && Instant.now().plusSeconds(REFRESH_SKEW_SECONDS).isBefore(expiry)) {
			return new Refreshed(tokens, false);
		}
		if (!StringUtils.hasText(tokens.getRefreshToken())) {
			throw reauthRequired();
		}
		assertConfigured(provider);
		var body = postForm(tokenUrl(), Map.of(
			"grant_type", "refresh_token",
			"client_id", properties.getMicrosoft().getClientId(),
			"client_secret", properties.getMicrosoft().getClientSecret(),
			"scope", MICROSOFT_SCOPES,
			"refresh_token", tokens.getRefreshToken()));
		return new Refreshed(tokensFrom(body, tokens.getRefreshToken()), true);
	}

	// -------------------------------------------------------------------------

	private MailboxTokens tokensFrom(JsonNode body, String previousRefreshToken) throws QorvaException {
		var accessToken = body.path("access_token").asText(null);
		if (!StringUtils.hasText(accessToken)) {
			throw reauthRequired();
		}
		// Microsoft rotates refresh tokens; keep the previous one only when none came back.
		var refreshToken = body.path("refresh_token").asText(null);
		return MailboxTokens.builder()
			.accessToken(accessToken)
			.refreshToken(StringUtils.hasText(refreshToken) ? refreshToken : previousRefreshToken)
			.expiresAt(Instant.now().plusSeconds(body.path("expires_in").asLong(3600)))
			.scope(body.path("scope").asText(null))
			.build();
	}

	private JsonNode postForm(String url, Map<String, String> form) throws QorvaException {
		var encoded = new StringBuilder();
		form.forEach((k, v) -> {
			if (!encoded.isEmpty()) encoded.append('&');
			encoded.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
				.append('=')
				.append(URLEncoder.encode(v, StandardCharsets.UTF_8));
		});
		try {
			var response = restClient.post().uri(url)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(encoded.toString())
				.retrieve()
				.body(String.class);
			return objectMapper.readTree(response != null ? response : "{}");
		} catch (RestClientResponseException e) {
			// invalid_grant = revoked consent / expired refresh token / password change: reconnect.
			var text = e.getResponseBodyAsString();
			log.warn("Mailbox token request to {} failed: {} {}", url, e.getStatusCode(), abbreviate(text));
			if (text != null && text.contains("invalid_grant")) {
				throw reauthRequired();
			}
			throw new QorvaException(QorvaErrorCodes.MAILBOX_SEND_FAILED,
				HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
		} catch (Exception e) {
			log.warn("Mailbox token request to {} failed: {}", url, e.getMessage());
			throw new QorvaException(QorvaErrorCodes.MAILBOX_SEND_FAILED,
				HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
		}
	}

	private String tokenUrl() {
		return authority() + "/oauth2/v2.0/token";
	}

	private String authority() {
		return properties.getMicrosoft().getAuthority().replaceAll("/+$", "");
	}

	private void assertConfigured(MailboxProviderEnum provider) throws QorvaException {
		if (!isConfigured(provider)) {
			throw new QorvaException(QorvaErrorCodes.MAILBOX_NOT_CONFIGURED,
				HttpStatus.SERVICE_UNAVAILABLE.value(), HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	private String signState(String tenantId, String userId, MailboxProviderEnum provider) {
		return stateSigner().sign(List.of(tenantId, userId, provider.name(),
			String.valueOf(Instant.now().getEpochSecond() + STATE_TTL_SECONDS), UUID.randomUUID().toString()));
	}

	private OauthStateSigner stateSigner() {
		return new OauthStateSigner(properties.getCredentialsKey());
	}

	static QorvaException reauthRequired() {
		return new QorvaException(QorvaErrorCodes.MAILBOX_REAUTH_REQUIRED,
			HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
	}

	private static String abbreviate(String text) {
		if (text == null) return "";
		return text.length() > 300 ? text.substring(0, 300) + "…" : text;
	}
}
