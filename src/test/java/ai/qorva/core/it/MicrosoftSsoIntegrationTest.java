package ai.qorva.core.it;

import ai.qorva.core.service.sso.MicrosoftSsoClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Sign in with Microsoft" end to end, with Microsoft's side (code exchange, ID token signature) stubbed: the
 * redirect carries a signed state, a nonce and PKCE; the callback signs in only an existing active user whose email
 * Microsoft vouches for and hands the app a single-use code; a company can require Microsoft sign-in, which then
 * refuses passwords for everyone but its owner.
 */
class MicrosoftSsoIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;
	private static final String CLIENT_ID = "qorva-entra-app";
	private static final String TID = "72f988bf-86f1-41af-91ab-2d7cd011db47";

	@MockitoBean
	private MicrosoftSsoClient client;
	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private ObjectMapper objectMapper;

	private TwoTenantFixture.SeededTenant a;

	@BeforeEach
	void seed() {
		a = fixture.reset().a();
		when(client.isConfigured()).thenReturn(true);
		when(client.clientId()).thenReturn(CLIENT_ID);
		when(client.authority()).thenReturn("https://login.microsoftonline.com/common");
		when(client.exchangeCode(eq("ms-code"), any(), any(), any())).thenReturn("id-token");
	}

	private Map<String, String> query(String location) {
		var params = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
		return Map.of("state", String.valueOf(params.getFirst("state")), "nonce", String.valueOf(params.getFirst("nonce")),
			"challenge", String.valueOf(params.getFirst("code_challenge")));
	}

	private void microsoftSays(String nonce, String preferredUsername) {
		var jwt = Jwt.withTokenValue("id-token").header("alg", "RS256")
			.claim("aud", List.of(CLIENT_ID)).claim("iss", "https://login.microsoftonline.com/" + TID + "/v2.0")
			.claim("tid", TID).claim("nonce", nonce).claim("preferred_username", preferredUsername)
			.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
		when(client.decode("id-token")).thenReturn(jwt);
	}

	/** start → Microsoft (stubbed) → callback; returns the callback's redirect. */
	private String signIn(String preferredUsername) throws Exception {
		var start = mvc.perform(get("/auth/sso/microsoft/start").param("email", "x@acme.test"))
			.andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
		assertThat(start).startsWith("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?")
			.contains("client_id=" + CLIENT_ID).contains("code_challenge_method=S256").contains("login_hint=");
		var q = query(start);
		microsoftSays(q.get("nonce"), preferredUsername);
		return mvc.perform(get("/auth/sso/microsoft/callback").param("code", "ms-code").param("state", q.get("state")))
			.andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
	}

	private String codeFrom(String location) {
		assertThat(location).startsWith("http://localhost:5173/login?sso=");
		return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("sso");
	}

	@Test
	void anInvitedUserSignsInWithMicrosoftAndTheCodeWorksOnce() throws Exception {
		var code = codeFrom(signIn(a.viewerEmail().toUpperCase()));

		mvc.perform(post("/auth/sso/exchange").contentType(JSON).content("{\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.jwt.access_token").isNotEmpty())
			.andExpect(jsonPath("$.data.user.email").value(a.viewerEmail()));
		mvc.perform(post("/auth/sso/exchange").contentType(JSON).content("{\"code\":\"" + code + "\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.errorCode").value("error.auth.sso_code_invalid"));
	}

	@Test
	void microsoftNeverCreatesAccountsAndATamperedStateIsRefused() throws Exception {
		assertThat(signIn("stranger@elsewhere.test")).isEqualTo("http://localhost:5173/login?ssoError=no_account");

		var start = mvc.perform(get("/auth/sso/microsoft/start")).andReturn().getResponse().getHeader("Location");
		var q = query(start);
		microsoftSays(q.get("nonce"), a.ownerEmail());
		mvc.perform(get("/auth/sso/microsoft/callback").param("code", "ms-code").param("state", q.get("state") + "x"))
			.andExpect(status().isFound())
			.andExpect(result -> assertThat(result.getResponse().getHeader("Location")).endsWith("ssoError=failed"));
		mvc.perform(get("/auth/sso/microsoft/callback").param("error", "access_denied"))
			.andExpect(result -> assertThat(result.getResponse().getHeader("Location")).endsWith("ssoError=cancelled"));
	}

	@Test
	void aTokenForAnotherNonceIsRefused() throws Exception {
		var start = mvc.perform(get("/auth/sso/microsoft/start")).andReturn().getResponse().getHeader("Location");
		microsoftSays("someone-elses-nonce", a.ownerEmail());
		mvc.perform(get("/auth/sso/microsoft/callback").param("code", "ms-code").param("state", query(start).get("state")))
			.andExpect(result -> assertThat(result.getResponse().getHeader("Location")).endsWith("ssoError=failed"));
	}

	@Test
	void requiringMicrosoftSignInRefusesPasswordsExceptForTheOwner() throws Exception {
		var owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		var viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
		mvc.perform(get("/auth/sso/availability")).andExpect(jsonPath("$.microsoft").value(true));

		mvc.perform(patch("/tenants/sso").header("Authorization", viewer).contentType(JSON).content("{\"ssoRequired\":true}"))
			.andExpect(status().isForbidden());
		mvc.perform(patch("/tenants/sso").header("Authorization", owner).contentType(JSON).content("{\"ssoRequired\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ssoRequired").value(true));

		mvc.perform(post("/auth/login").contentType(JSON)
				.content("{\"email\":\"" + a.viewerEmail() + "\",\"rawPassword\":\"" + TwoTenantFixture.PASSWORD + "\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorCode").value("error.auth.sso_required"));
		// Break-glass: the owner keeps the password.
		mvc.perform(post("/auth/login").contentType(JSON)
				.content("{\"email\":\"" + a.ownerEmail() + "\",\"rawPassword\":\"" + TwoTenantFixture.PASSWORD + "\"}"))
			.andExpect(status().isOk());
		// Microsoft sign-in still works for the viewer.
		var code = codeFrom(signIn(a.viewerEmail()));
		mvc.perform(post("/auth/sso/exchange").contentType(JSON).content("{\"code\":\"" + code + "\"}")).andExpect(status().isOk());
	}

	@Test
	void withoutTheEntraAppTheButtonIsHiddenAndTheSettingRefused() throws Exception {
		when(client.isConfigured()).thenReturn(false);
		mvc.perform(get("/auth/sso/availability")).andExpect(jsonPath("$.microsoft").value(false));
		mvc.perform(get("/auth/sso/microsoft/start"))
			.andExpect(result -> assertThat(result.getResponse().getHeader("Location")).endsWith("ssoError=not_configured"));
		mvc.perform(patch("/tenants/sso").header("Authorization", fixture.bearer(a.ownerEmail(), a.tenantId()))
				.contentType(JSON).content("{\"ssoRequired\":true}"))
			.andExpect(status().isServiceUnavailable());
	}
}
