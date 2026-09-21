package ai.qorva.core.service.mailbox;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.config.MailboxProperties;
import ai.qorva.core.enums.MailboxProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailboxOauthServiceTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";
	private static final String USER = "64b0c1a2e4b0f2a1b2c3d4e6";

	private MailboxProperties properties;
	private MailboxOauthService service;

	@BeforeEach
	void setUp() {
		properties = new MailboxProperties();
		properties.setCredentialsKey("unit-test-mailbox-key");
		properties.getMicrosoft().setClientId("ms-client");
		properties.getMicrosoft().setClientSecret("ms-secret");
		var ats = new AtsProperties();
		ats.setPublicBaseUrl("https://api.qorva.ai");
		service = new MailboxOauthService(properties, ats, RestClient.builder(), new ObjectMapper().findAndRegisterModules());
	}

	@Test
	void consentUrlCarriesDelegatedScopesRedirectAndSignedState() throws QorvaException {
		var url = service.buildConsentUrl(MailboxProviderEnum.MICROSOFT, TENANT, USER);

		assertThat(url).startsWith("https://login.microsoftonline.com/common/oauth2/v2.0/authorize");
		assertThat(url).contains("client_id=ms-client", "response_type=code", "prompt=select_account");
		var decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
		assertThat(decoded).contains("redirect_uri=https://api.qorva.ai/integrations/mailbox/microsoft/oauth/callback");
		assertThat(decoded).contains("offline_access", "Mail.Send", "User.Read");

		var state = url.substring(url.indexOf("&state=") + "&state=".length());
		var claims = service.validateState(state);
		assertThat(claims.tenantId()).isEqualTo(TENANT);
		assertThat(claims.userId()).isEqualTo(USER);
		assertThat(claims.provider()).isEqualTo(MailboxProviderEnum.MICROSOFT);
	}

	@Test
	void tamperedStateIsRejectedWithTheMailboxErrorCode() throws QorvaException {
		var url = service.buildConsentUrl(MailboxProviderEnum.MICROSOFT, TENANT, USER);
		var state = url.substring(url.indexOf("&state=") + "&state=".length());

		assertThatThrownBy(() -> service.validateState(state.substring(0, state.length() - 2) + "ff"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.MAILBOX_OAUTH_STATE_INVALID);
	}

	@Test
	void unconfiguredClientAnswersServiceUnavailable() {
		properties.getMicrosoft().setClientId("");

		assertThat(service.isConfigured(MailboxProviderEnum.MICROSOFT)).isFalse();
		assertThatThrownBy(() -> service.buildConsentUrl(MailboxProviderEnum.MICROSOFT, TENANT, USER))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.MAILBOX_NOT_CONFIGURED);
	}

	@Test
	void freshTokenIsReturnedUntouchedWhenNotNearExpiry() throws QorvaException {
		var tokens = MailboxTokens.builder()
			.accessToken("a").refreshToken("r").expiresAt(Instant.now().plusSeconds(3600)).build();

		var refreshed = service.ensureFreshToken(MailboxProviderEnum.MICROSOFT, tokens);

		assertThat(refreshed.changed()).isFalse();
		assertThat(refreshed.tokens()).isSameAs(tokens);
	}

	@Test
	void expiredTokenWithoutRefreshTokenNeedsReauth() {
		var tokens = MailboxTokens.builder()
			.accessToken("a").expiresAt(Instant.now().minusSeconds(10)).build();

		assertThatThrownBy(() -> service.ensureFreshToken(MailboxProviderEnum.MICROSOFT, tokens))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.MAILBOX_REAUTH_REQUIRED);
	}
}
