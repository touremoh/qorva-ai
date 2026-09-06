package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtsOauthServiceTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";

	@Mock private AtsHttpClient http;
	@Mock private AtsConnectionRepository connectionRepository;

	private AtsProperties properties;
	private AtsOauthService service;

	@BeforeEach
	void setUp() {
		properties = new AtsProperties();
		properties.setCredentialsKey("unit-test-key");
		properties.setPublicBaseUrl("https://api.qorva.ai");
		var greenhouse = new AtsProperties.OauthClient();
		greenhouse.setClientId("gh-client");
		greenhouse.setClientSecret("gh-secret");
		properties.getOauth().put("greenhouse", greenhouse);
		var cipher = new CredentialsCipher(properties, new ObjectMapper().findAndRegisterModules());
		service = new AtsOauthService(properties, http, cipher, connectionRepository);
	}

	@Test
	void greenhouseRedirectUriIsProviderScopedUnlessOverridden() {
		assertThat(service.redirectUri(AtsProviderEnum.GREENHOUSE))
			.isEqualTo("https://api.qorva.ai/integrations/greenhouse/oauth/callback");

		properties.getOauth().get("greenhouse").setRedirectUri("https://api.qorva.ai/custom/callback");
		assertThat(service.redirectUri(AtsProviderEnum.GREENHOUSE))
			.isEqualTo("https://api.qorva.ai/custom/callback");
	}

	@Test
	void greenhouseConsentUrlCarriesClientScopesAndSignedState() throws QorvaException {
		var url = service.buildConsentUrl(AtsProviderEnum.GREENHOUSE, TENANT);

		assertThat(url).startsWith("https://api.greenhouse.io/oauth/authorize");
		assertThat(url).contains("client_id=gh-client", "response_type=code");
		assertThat(decoded(url)).contains("redirect_uri=https://api.qorva.ai/integrations/greenhouse/oauth/callback");
		assertThat(decoded(url)).contains("harvest:candidates:list", "harvest:jobs:list");

		var state = url.substring(url.indexOf("&state=") + "&state=".length());
		var claims = service.validateState(state);
		assertThat(claims.tenantId()).isEqualTo(TENANT);
		assertThat(claims.provider()).isEqualTo(AtsProviderEnum.GREENHOUSE);
	}

	@Test
	void configuredScopesOverrideTheDefaults() throws QorvaException {
		properties.getOauth().get("greenhouse").setScopes("harvest:candidates:list harvest:jobs:list");

		assertThat(decoded(service.buildConsentUrl(AtsProviderEnum.GREENHOUSE, TENANT)))
			.doesNotContain("harvest:candidates:notes:create");
	}

	@Test
	void greenhouseCodeExchangeStoresBothTokens() throws Exception {
		when(http.postForm(eq(AtsProviderEnum.GREENHOUSE), eq("https://api.greenhouse.io/oauth/token"), anyMap()))
			.thenReturn(new ObjectMapper().readTree(
				"{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":3600}"));

		var credentials = service.exchangeCode(AtsProviderEnum.GREENHOUSE, "the-code", null);

		assertThat(credentials.getAccessToken()).isEqualTo("at");
		assertThat(credentials.getRefreshToken()).isEqualTo("rt");
		assertThat(credentials.getTokenExpiresAt()).isAfter(Instant.now());
		assertThat(credentials.getDatacenter()).isNull();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> form = ArgumentCaptor.forClass(Map.class);
		verify(http).postForm(eq(AtsProviderEnum.GREENHOUSE), eq("https://api.greenhouse.io/oauth/token"), form.capture());
		assertThat(form.getValue())
			.containsEntry("grant_type", "authorization_code")
			.containsEntry("code", "the-code")
			.containsEntry("redirect_uri", "https://api.qorva.ai/integrations/greenhouse/oauth/callback");
	}

	@Test
	void expiredGreenhouseTokenIsRefreshedAndPersisted() throws Exception {
		when(http.postForm(eq(AtsProviderEnum.GREENHOUSE), eq("https://api.greenhouse.io/oauth/token"), anyMap()))
			.thenReturn(new ObjectMapper().readTree("{\"access_token\":\"fresh\",\"expires_in\":3600}"));
		var connection = AtsConnection.builder().id("c1").provider("greenhouse").build();
		var stale = AtsCredentials.builder()
			.accessToken("old").refreshToken("rt").tokenExpiresAt(Instant.now().minusSeconds(60)).build();

		var refreshed = service.ensureFreshToken(connection, stale);

		assertThat(refreshed.getAccessToken()).isEqualTo("fresh");
		// The rotated token is re-encrypted onto the connection, never stored in the clear.
		assertThat(connection.getEncryptedCredentials()).isNotNull().doesNotContain("fresh");
		verify(connectionRepository).save(connection);
	}

	@Test
	void refreshWithoutARefreshTokenFailsAsAnAuthError() {
		var connection = AtsConnection.builder().id("c1").provider("greenhouse").build();
		var stale = AtsCredentials.builder().accessToken("old").tokenExpiresAt(Instant.now().minusSeconds(60)).build();

		assertThatThrownBy(() -> service.ensureFreshToken(connection, stale))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.ATS_AUTH_FAILED);
	}

	// ------------------------------------------------------------------ Zoho datacenters

	private void givenZohoClient() {
		var zoho = new AtsProperties.OauthClient();
		zoho.setClientId("zoho-client");
		zoho.setClientSecret("zoho-secret");
		properties.getOauth().put("zoho_recruit", zoho);
	}

	@Test
	void zohoConsentStartsOnTheChosenDatacenterAndTheRegionSurvivesTheState() throws QorvaException {
		givenZohoClient();

		var url = service.buildConsentUrl(AtsProviderEnum.ZOHO_RECRUIT, TENANT, "eu");

		assertThat(url).startsWith("https://accounts.zoho.eu/oauth/v2/auth");
		var state = url.substring(url.indexOf("&state=") + "&state=".length());
		assertThat(service.validateState(state).datacenter()).isEqualTo("eu");
	}

	@Test
	void zohoConsentDefaultsToTheUsDatacenterWhenNoRegionIsGiven() throws QorvaException {
		givenZohoClient();

		assertThat(service.buildConsentUrl(AtsProviderEnum.ZOHO_RECRUIT, TENANT, null))
			.startsWith("https://accounts.zoho.com/oauth/v2/auth");
	}

	@Test
	void anUnknownZohoRegionIsRejectedRatherThanSentToTheWrongDatacenter() {
		givenZohoClient();

		assertThatThrownBy(() -> service.buildConsentUrl(AtsProviderEnum.ZOHO_RECRUIT, TENANT, "elbonia"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.HTTP_VALIDATION);
	}

	@Test
	void canadaIsRecognisedAsItsOwnDatacenterNotTheUsDefault() throws Exception {
		givenZohoClient();
		// accounts.zohocloud.ca does not contain "accounts.zoho." — the old parse read it as US.
		when(http.postForm(eq(AtsProviderEnum.ZOHO_RECRUIT),
			eq("https://accounts.zohocloud.ca/oauth/v2/token"), anyMap()))
			.thenReturn(new ObjectMapper().readTree(
				"{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":3600,"
					+ "\"api_domain\":\"https://www.zohoapis.ca\"}"));

		var credentials = service.exchangeCode(
			AtsProviderEnum.ZOHO_RECRUIT, "code", "https://accounts.zohocloud.ca", null);

		assertThat(credentials.getDatacenter()).isEqualTo("ca");
		assertThat(credentials.getApiDomain()).isEqualTo("https://www.zohoapis.ca");
	}

	@Test
	void theStateRegionRedeemsTheCodeWhenZohoEchoesNoAccountsServer() throws Exception {
		givenZohoClient();
		when(http.postForm(eq(AtsProviderEnum.ZOHO_RECRUIT),
			eq("https://accounts.zoho.in/oauth/v2/token"), anyMap()))
			.thenReturn(new ObjectMapper().readTree(
				"{\"access_token\":\"at\",\"expires_in\":3600,\"api_domain\":\"https://www.zohoapis.in\"}"));

		var credentials = service.exchangeCode(AtsProviderEnum.ZOHO_RECRUIT, "code", null, "in");

		assertThat(credentials.getDatacenter()).isEqualTo("in");
		assertThat(credentials.getApiDomain()).isEqualTo("https://www.zohoapis.in");
	}

	@Test
	void anUnrecognisedAccountsServerIsIgnoredInFavourOfTheSignedRegion() throws Exception {
		givenZohoClient();
		when(http.postForm(eq(AtsProviderEnum.ZOHO_RECRUIT),
			eq("https://accounts.zoho.eu/oauth/v2/token"), anyMap()))
			.thenReturn(new ObjectMapper().readTree("{\"access_token\":\"at\",\"expires_in\":3600}"));

		// A callback parameter is attacker-influenced; only hosts from the registry are used.
		var credentials = service.exchangeCode(
			AtsProviderEnum.ZOHO_RECRUIT, "code", "https://accounts.evil.example.com", "eu");

		assertThat(credentials.getDatacenter()).isEqualTo("eu");
	}

	@Test
	void zohoRefreshGoesToTheAccountDatacenterAndKeepsApiDomainCurrent() throws Exception {
		givenZohoClient();
		when(http.postForm(eq(AtsProviderEnum.ZOHO_RECRUIT),
			eq("https://accounts.zoho.eu/oauth/v2/token"), anyMap()))
			.thenReturn(new ObjectMapper().readTree(
				"{\"access_token\":\"fresh\",\"expires_in\":3600,\"api_domain\":\"https://www.zohoapis.eu\"}"));
		var connection = AtsConnection.builder().id("c1").provider("zoho_recruit").build();
		var stale = AtsCredentials.builder()
			.accessToken("old").refreshToken("rt").datacenter("eu")
			.tokenExpiresAt(Instant.now().minusSeconds(60)).build();

		var refreshed = service.ensureFreshToken(connection, stale);

		assertThat(refreshed.getAccessToken()).isEqualTo("fresh");
		assertThat(refreshed.getApiDomain()).isEqualTo("https://www.zohoapis.eu");
	}

	@Test
	void everyOfferedRegionResolvesToAConsentHost() throws QorvaException {
		givenZohoClient();
		// The offered list drives the connect dropdown: an entry with no host would render a
		// choice that sends the recruiter to the US instead.
		assertThat(AtsOauthService.zohoRegions()).startsWith("com", "eu", "in");

		for (String region : AtsOauthService.zohoRegions()) {
			var url = service.buildConsentUrl(AtsProviderEnum.ZOHO_RECRUIT, TENANT, region);
			assertThat(url).startsWith("https://accounts.zoho");
			// Round-trips: the host the region resolves to maps back to that same region.
			var state = url.substring(url.indexOf("&state=") + "&state=".length());
			assertThat(service.validateState(state).datacenter()).isEqualTo(region);
		}
	}

	@Test
	void consentUrlRequiresAConfiguredClient() {
		properties.getOauth().clear();

		assertThatThrownBy(() -> service.buildConsentUrl(AtsProviderEnum.GREENHOUSE, TENANT))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.ATS_OAUTH_NOT_CONFIGURED);
	}

	@Test
	void tamperedStateIsRejected() throws QorvaException {
		var url = service.buildConsentUrl(AtsProviderEnum.GREENHOUSE, TENANT);
		var state = url.substring(url.indexOf("&state=") + "&state=".length());

		assertThatThrownBy(() -> service.validateState(state.substring(0, state.length() - 2) + "ff"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.ATS_OAUTH_STATE_INVALID);
	}

	private static String decoded(String url) {
		return URLDecoder.decode(url, StandardCharsets.UTF_8);
	}
}
