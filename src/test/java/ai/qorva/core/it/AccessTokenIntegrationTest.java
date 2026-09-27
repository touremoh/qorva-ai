package ai.qorva.core.it;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.utils.JwtUtils;
import io.jsonwebtoken.Jwts;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Which bearer tokens authenticate a request (the JWT filter), against the real user records. */
class AccessTokenIntegrationTest extends AbstractIntegrationTest {

	@Autowired private TwoTenantFixture fixture;
	@Autowired private JwtConfig jwtConfig;
	@Autowired private QorvaUserDetailsService userDetailsService;
	@Autowired private MongoTemplate mongo;

	private TwoTenantFixture.SeededTenant a;
	private TwoTenantFixture.SeededTenant b;

	@BeforeEach
	void seed() {
		var seeded = fixture.reset();
		a = seeded.a();
		b = seeded.b();
	}

	@Test
	void aLoginToken_isAccepted() throws Exception {
		assertThat(status(fixture.bearer(a.ownerEmail(), a.tenantId()))).isEqualTo(200);
	}

	@Test
	void aSetPasswordLinkToken_isNotAnAccessToken() throws Exception {
		var link = JwtUtils.generateSetPasswordToken(a.ownerId(), 0, jwtConfig, 3_600_000L);
		assertThat(status("Bearer " + link)).isEqualTo(403);
	}

	@Test
	void aTokenNamingAnotherTenant_isRefused() throws Exception {
		var tenantB = new TenantDTO();
		tenantB.setId(b.tenantId());
		var forged = JwtUtils.generateToken(userDetailsService.loadUserByUsername(a.ownerEmail()), jwtConfig, tenantB);
		assertThat(status("Bearer " + forged)).isEqualTo(403);
	}

	@Test
	void aLockedAccount_stopsWorkingAtOnce() throws Exception {
		var token = fixture.bearer(a.ownerEmail(), a.tenantId());
		mongo.getCollection("users").updateOne(new Document("_id", new ObjectId(a.ownerId())),
			new Document("$set", new Document("userAccountStatus", "LOCKED")));
		assertThat(status(token)).isEqualTo(403);
	}

	@Test
	void aTokenIssuedBeforeTheTypeClaim_stillWorksUntilItExpires() throws Exception {
		var legacy = Jwts.builder()
			.claims(Map.of("tenantId", a.tenantId()))
			.subject(a.ownerEmail())
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + 600_000))
			.signWith(jwtConfig.getSecretKey())
			.compact();
		assertThat(status("Bearer " + legacy)).isEqualTo(200);
	}

	@Test
	void changingThePassword_endsOtherSessions_butTheReturnedTokenWorks() throws Exception {
		var otherSession = fixture.bearer(a.ownerEmail(), a.tenantId());
		var response = mvc.perform(patch("/users/" + a.ownerId() + "/password")
				.header("Authorization", otherSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + TwoTenantFixture.PASSWORD + "\",\"newPassword\":\"Brand-New-Horse-7\"}"))
			.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(200);
		var fresh = new ObjectMapper().readTree(response.getContentAsString()).path("data").path("access_token").asText();

		assertThat(status(otherSession)).isEqualTo(403);
		assertThat(status("Bearer " + fresh)).isEqualTo(200);
		// Refresh needs an accepted token: the filter refuses a revoked one before the service runs.
		assertThat(refresh(otherSession)).isEqualTo(403);
		assertThat(validate(otherSession)).isEqualTo(401);
	}

	@Test
	void aUserCannotChangeSomeoneElsesPassword() throws Exception {
		var status = mvc.perform(patch("/users/" + a.ownerId() + "/password")
				.header("Authorization", fixture.bearer(a.viewerEmail(), a.tenantId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"" + TwoTenantFixture.PASSWORD + "\",\"newPassword\":\"Brand-New-Horse-7\"}"))
			.andReturn().getResponse().getStatus();
		assertThat(status).isEqualTo(403);
		assertThat(status(fixture.bearer(a.ownerEmail(), a.tenantId()))).isEqualTo(200);
	}

	@Test
	void aSetPasswordLink_cannotBeRefreshedIntoAnAccessToken() throws Exception {
		var link = JwtUtils.generateSetPasswordToken(a.ownerId(), 0, jwtConfig, 3_600_000L);
		assertThat(refresh("Bearer " + link)).isEqualTo(403);
	}

	@Test
	void aLiveToken_isRefreshed() throws Exception {
		assertThat(refresh(fixture.bearer(a.ownerEmail(), a.tenantId()))).isEqualTo(200);
	}

	private int refresh(String bearer) throws Exception {
		return mvc.perform(post("/auth/token/refresh").header("Authorization", bearer)).andReturn().getResponse().getStatus();
	}

	private int validate(String bearer) throws Exception {
		return mvc.perform(post("/auth/token/validate").header("Authorization", bearer)).andReturn().getResponse().getStatus();
	}

	private int status(String bearer) throws Exception {
		return mvc.perform(get("/users/me/mfa").header("Authorization", bearer)).andReturn().getResponse().getStatus();
	}
}
