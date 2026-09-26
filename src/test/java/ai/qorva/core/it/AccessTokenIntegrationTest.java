package ai.qorva.core.it;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.utils.JwtUtils;
import io.jsonwebtoken.Jwts;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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

	private int status(String bearer) throws Exception {
		return mvc.perform(get("/users/me/mfa").header("Authorization", bearer)).andReturn().getResponse().getStatus();
	}
}
