package ai.qorva.core.service.sso;

import ai.qorva.core.config.MailboxProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The two calls to Microsoft: trade the authorization code for tokens, and verify the ID token's signature (keys
 * from the authority's JWKS, cached by the decoder) and lifetime. Which claims are acceptable is
 * {@link MicrosoftSsoService}'s decision. Uses the mailbox's multi-tenant Entra app registration.
 */
@Slf4j
@Component
public class MicrosoftSsoClient {

	private final MailboxProperties properties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private volatile JwtDecoder decoder;

	public MicrosoftSsoClient(MailboxProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
		this.properties = properties;
		this.restClient = builder.build();
		this.objectMapper = objectMapper;
	}

	public boolean isConfigured() {
		return properties.getMicrosoft().isConfigured();
	}

	public String clientId() {
		return properties.getMicrosoft().getClientId();
	}

	public String authority() {
		return properties.getMicrosoft().getAuthority().replaceAll("/+$", "");
	}

	/** The ID token from the code exchange; null when Microsoft refused it. */
	public String exchangeCode(String code, String redirectUri, String codeVerifier, String scope) {
		var form = Map.of(
			"grant_type", "authorization_code",
			"client_id", clientId(),
			"client_secret", properties.getMicrosoft().getClientSecret(),
			"redirect_uri", redirectUri,
			"scope", scope,
			"code", code,
			"code_verifier", codeVerifier);
		var encoded = new StringBuilder();
		form.forEach((k, v) -> {
			if (!encoded.isEmpty()) encoded.append('&');
			encoded.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
		});
		try {
			var body = restClient.post().uri(authority() + "/oauth2/v2.0/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(encoded.toString())
				.retrieve()
				.body(String.class);
			return objectMapper.readTree(body == null ? "{}" : body).path("id_token").asText(null);
		} catch (Exception e) {
			log.warn("Microsoft sign-in: code exchange refused: {}", e.getMessage());
			return null;
		}
	}

	/** Signature and expiry checked; throws on a forged or expired token. */
	public Jwt decode(String idToken) {
		return decoder().decode(idToken);
	}

	private JwtDecoder decoder() {
		var current = decoder;
		if (current == null) {
			synchronized (this) {
				if (decoder == null) {
					var nimbus = NimbusJwtDecoder.withJwkSetUri(authority() + "/discovery/v2.0/keys").build();
					nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator()));
					decoder = nimbus;
				}
				current = decoder;
			}
		}
		return current;
	}
}
