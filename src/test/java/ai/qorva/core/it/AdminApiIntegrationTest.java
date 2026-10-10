package ai.qorva.core.it;

import ai.qorva.core.admin.security.AdminTokens;
import ai.qorva.core.admin.service.AdminNotificationService;
import ai.qorva.core.admin.service.AdminStripeGateway;
import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.entity.PlatformAdmin;
import ai.qorva.core.dao.entity.ProductReference;
import ai.qorva.core.dto.common.StripePrice;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.TenantAccess;
import ai.qorva.core.service.TenantPurgeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The admin console's API against the real application: the two realms never accept each other's tokens, sign-in
 * needs the emailed code, roles hold, company status reaches sign-in and live sessions, test accounts expire and are
 * reactivated only with a new password, and a purge removes one tenant entirely and nothing of the other.
 */
class AdminApiIntegrationTest extends AbstractIntegrationTest {

	private static final String SCALE_PRODUCT = "prod_test_scale";
	private static final String SCALE_PRICE = "price_test_scale_month";

	@MockitoBean private AdminNotificationService notifications;
	@MockitoBean private AdminStripeGateway stripe;

	@Autowired private TwoTenantFixture fixture;
	@Autowired private MongoTemplate mongo;
	@Autowired private AdminTokens tokens;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private QorvaProductProperties productProperties;
	@Autowired private TenantPurgeService purgeService;
	@Autowired private TenantAccess tenantAccess;
	@Autowired private ObjectMapper json;
	@Autowired private ai.qorva.core.config.JwtConfig jwtConfig;

	private TwoTenantFixture.SeededTenant a;
	private TwoTenantFixture.SeededTenant b;
	private String owner;
	private String support;

	@BeforeEach
	void seed() {
		var seeded = fixture.reset();
		a = seeded.a();
		b = seeded.b();
		owner = "Bearer " + tokens.session(admin("owner@qorva.test", PlatformAdmin.ROLE_OWNER, "Owner-Pass-1")).token();
		support = "Bearer " + tokens.session(admin("support@qorva.test", PlatformAdmin.ROLE_SUPPORT, "Support-Pass-1")).token();
		mongo.save(scaleProduct());
		tenantAccess.evict();
	}

	// ---- realms ------------------------------------------------------------------

	@Test
	void aTenantToken_neverReachesTheAdminApi() throws Exception {
		assertThat(status(get("/admin/tenants"), fixture.bearer(a.ownerEmail(), a.tenantId()))).isEqualTo(401);
		assertThat(status(get("/admin/tenants"), null)).isEqualTo(401);
	}

	@Test
	void anAdminToken_neverReachesATenantRoute() throws Exception {
		assertThat(status(get("/cvs"), owner)).isEqualTo(403);
	}

	@Test
	void anAdminTokenSignedWithTheTenantKey_isRefused() throws Exception {
		var forged = Jwts.builder().subject(adminId("owner@qorva.test")).claim("typ", "admin").claim("cv", 0)
			.expiration(java.util.Date.from(Instant.now().plusSeconds(600)))
			.signWith(jwtConfig.getSecretKey()).compact();
		assertThat(status(get("/admin/tenants"), "Bearer " + forged)).isEqualTo(401);
	}

	// ---- sign-in and roles -----------------------------------------------------------

	@Test
	void signIn_needsThePasswordAndTheEmailedCode() throws Exception {
		assertThat(status(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"owner@qorva.test\",\"password\":\"wrong\"}"), null)).isEqualTo(401);

		var challenge = body(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"OWNER@qorva.test\",\"password\":\"Owner-Pass-1\"}"), null);
		var code = ArgumentCaptor.forClass(String.class);
		verify(notifications).sendCode(any(), code.capture(), anyLong());

		var wrong = mvc.perform(post("/admin/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"challengeId\":\"" + challenge.get("challengeId").asText() + "\",\"code\":\"000000x\"}")).andReturn().getResponse();
		assertThat(wrong.getStatus()).isEqualTo(401);

		var session = body(post("/admin/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"challengeId\":\"" + challenge.get("challengeId").asText() + "\",\"code\":\"" + code.getValue() + "\"}"), null);
		var me = body(get("/admin/auth/me"), "Bearer " + session.get("accessToken").asText());
		assertThat(me.get("role").asText()).isEqualTo("OWNER");
	}

	@Test
	void support_readsButCannotChangeCompanies() throws Exception {
		assertThat(status(get("/admin/tenants"), support)).isEqualTo(200);
		assertThat(status(post("/admin/tenants/" + a.tenantId() + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"SUSPENDED\"}"), support)).isEqualTo(403);
		assertThat(status(get("/admin/admins"), support)).isEqualTo(403);
	}

	@Test
	void aDisabledAdmin_losesTheSessionAtOnce() throws Exception {
		mongo.getCollection("platform_admins").updateOne(new Document("email", "support@qorva.test"),
			new Document("$set", new Document("status", "DISABLED")));
		assertThat(status(get("/admin/tenants"), support)).isEqualTo(401);
	}

	@Test
	void anyAdmin_changesTheirOwnName_withoutLosingTheSession_andAnOwnerRenamesOthers() throws Exception {
		var me = body(patch("/admin/auth/me").contentType(MediaType.APPLICATION_JSON)
			.content("{\"firstName\":\"  Sam \",\"lastName\":\"Support\"}"), support);
		assertThat(me.get("firstName").asText()).isEqualTo("Sam");
		assertThat(me.get("lastName").asText()).isEqualTo("Support");
		assertThat(status(get("/admin/auth/me"), support)).isEqualTo(200);

		var renamed = body(patch("/admin/admins/" + adminId("support@qorva.test")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"firstName\":\"Samuel\",\"lastName\":\"\"}"), owner);
		assertThat(renamed.get("firstName").asText()).isEqualTo("Samuel");
		assertThat(renamed.hasNonNull("lastName")).isFalse();
		assertThat(renamed.get("role").asText()).isEqualTo(PlatformAdmin.ROLE_SUPPORT);
		assertThat(status(get("/admin/tenants"), support)).isEqualTo(200);

		assertThat(status(patch("/admin/auth/me").contentType(MediaType.APPLICATION_JSON)
			.content("{\"firstName\":\"" + "x".repeat(101) + "\"}"), owner)).isEqualTo(400);
	}

	// ---- company status -----------------------------------------------------------------

	@Test
	void suspendingACompany_endsItsSessionsAndRefusesSignIn_untilReactivated() throws Exception {
		var live = fixture.bearer(a.ownerEmail(), a.tenantId());
		assertThat(status(post("/admin/tenants/" + a.tenantId() + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"SUSPENDED\",\"reason\":\"unpaid\"}"), owner)).isEqualTo(200);

		assertThat(status(get("/cvs"), live)).isEqualTo(403);
		assertThat(login(a.ownerEmail(), TwoTenantFixture.PASSWORD).get("errorCode").asText()).isEqualTo("error.auth.tenant_suspended");
		assertThat(status(get("/cvs"), fixture.bearer(b.ownerEmail(), b.tenantId()))).isEqualTo(200);

		assertThat(status(post("/admin/tenants/" + a.tenantId() + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"ACTIVE\"}"), owner)).isEqualTo(200);
		assertThat(login(a.ownerEmail(), TwoTenantFixture.PASSWORD).has("jwt")).isTrue();
		assertThat(mongo.getCollection("admin_audit_logs").countDocuments(new Document("targetTenantId", a.tenantId()))).isEqualTo(2);
	}

	@Test
	void aSuspendedCompanysJobsAreNotClaimed() {
		var job = mongo.insert(BackgroundJob.builder().tenantId(a.tenantId()).type(BackgroundJob.TYPE_REANALYZE)
			.status(BackgroundJob.STATUS_PENDING).createdAt(Instant.now()).build());
		mongo.getCollection("tenants").updateOne(new Document("_id", new ObjectId(a.tenantId())),
			new Document("$set", new Document("status", "SUSPENDED")));
		tenantAccess.evict();
		var claimable = mongo.find(org.springframework.data.mongodb.core.query.Query.query(
			tenantAccess.usableTenantsOnly("tenantId")), BackgroundJob.class);
		assertThat(claimable).extracting(BackgroundJob::getId).doesNotContain(job.getId());
	}

	// ---- test accounts -----------------------------------------------------------------

	@Test
	void aTester_signsInAtOnce_withTheTiersLimits_andTokensThatStopAtTheEnd() throws Exception {
		var until = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		var tester = createTester("john@tester.test", "Tester-Pass-1", until);
		assertThat(tester.get("state").asText()).isEqualTo("ACTIVE");
		assertThat(tester.get("tier").get("name").asText()).isEqualTo("Scale");

		var session = login("john@tester.test", "Tester-Pass-1");
		var exp = Jwts.parser().verifyWith(jwtConfig.getSecretKey()).build()
			.parseSignedClaims(session.get("jwt").get("access_token").asText()).getPayload().getExpiration().toInstant();
		assertThat(exp).isBeforeOrEqualTo(until.plusSeconds(1));
		var tenantId = tester.get("tenantId").asText();
		var usage = mongo.getCollection("usage_monitoring").find(new Document("tenantId", new ObjectId(tenantId))).first();
		assertThat(usage).isNotNull();
		assertThat(((Document) ((Document) usage.get("features")).get("screeningActions")).getInteger("limit"))
			.isEqualTo(productProperties.getScale().getFeatures().getLimits().getScreeningActions());
		assertThat(status(post("/stripe/portal-session"), "Bearer " + session.get("jwt").get("access_token").asText())).isEqualTo(409);
	}

	@Test
	void anExpiredTester_isRefused_andComesBackOnlyWithANewPassword() throws Exception {
		var tester = createTester("jane@tester.test", "Tester-Pass-1", Instant.now().plus(2, ChronoUnit.DAYS));
		var tenantId = tester.get("tenantId").asText();
		mongo.getCollection("tenants").updateOne(new Document("_id", new ObjectId(tenantId)),
			new Document("$set", new Document("accessExpiresAt", java.util.Date.from(Instant.now().minusSeconds(60)))));
		tenantAccess.evict();
		assertThat(login("jane@tester.test", "Tester-Pass-1").get("errorCode").asText()).isEqualTo("error.auth.account_expired");

		assertThat(status(post("/admin/tenants/" + tenantId + "/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"ACTIVE\"}"), owner)).isEqualTo(409);
		var until = Instant.now().plus(14, ChronoUnit.DAYS);
		assertThat(reactivate(tenantId, null, until)).isEqualTo(400);
		assertThat(reactivate(tenantId, "Tester-Pass-1", until)).isEqualTo(400);
		assertThat(reactivate(tenantId, "Tester-Pass-2", null)).isEqualTo(400);
		assertThat(reactivate(tenantId, "Tester-Pass-2", until)).isEqualTo(200);

		assertThat(login("jane@tester.test", "Tester-Pass-1").get("errorCode").asText()).isEqualTo("error.auth.authentication_failed");
		assertThat(login("jane@tester.test", "Tester-Pass-2").has("jwt")).isTrue();
	}

	@Test
	void deactivatingATester_endsTheSessionAndNeedsANewPasswordToReturn() throws Exception {
		var tester = createTester("duh@tester.test", "Tester-Pass-1", Instant.now().plus(5, ChronoUnit.DAYS));
		var tenantId = tester.get("tenantId").asText();
		var live = "Bearer " + login("duh@tester.test", "Tester-Pass-1").get("jwt").get("access_token").asText();

		assertThat(status(post("/admin/testers/" + tenantId + "/deactivate"), owner)).isEqualTo(200);
		assertThat(status(get("/cvs"), live)).isEqualTo(403);
		assertThat(reactivate(tenantId, "Tester-Pass-1", null)).isEqualTo(400);
		assertThat(reactivate(tenantId, "Tester-Pass-3", null)).isEqualTo(200);
		assertThat(login("duh@tester.test", "Tester-Pass-3").has("jwt")).isTrue();
	}

	// ---- delete and purge --------------------------------------------------------------

	@Test
	void deleteThenPurge_removesOneCompanyEntirely_andNothingOfTheOther() throws Exception {
		var bBefore = fixture.fingerprint(b.tenantId());
		assertThat(status(delete("/admin/tenants/" + a.tenantId()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"closed\"}"), owner)).isEqualTo(200);
		verify(stripe).setCancelAtPeriodEnd(any(), eq(true));
		assertThat(login(a.ownerEmail(), TwoTenantFixture.PASSWORD).get("errorCode").asText()).isEqualTo("error.auth.tenant_deleted");

		assertThat(status(post("/admin/tenants/" + a.tenantId() + "/purge").contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirmName\":\"wrong\"}"), owner)).isEqualTo(400);
		var job = body(post("/admin/tenants/" + a.tenantId() + "/purge").contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirmName\":\"" + a.tenantName() + "\"}"), owner);
		var purge = mongo.findById(new ObjectId(job.get("id").asText()), BackgroundJob.class);
		TenantScope.runAs(a.tenantId(), () -> purgeService.execute(purge));

		var left = fixture.fingerprint(a.tenantId()).lines()
			.filter(l -> !l.startsWith("stripe_event_logs:"))
			.filter(l -> !(l.startsWith("background_jobs:") && l.contains(BackgroundJob.TYPE_TENANT_PURGE)))
			.toList();
		assertThat(left).isEmpty();
		assertThat(fixture.fingerprint(b.tenantId())).isEqualTo(bBefore);
		assertThat(OrphanCheck.find(mongo)).isEmpty();
	}

	@Test
	void statistics_leaveTestAccountsOutByDefault() throws Exception {
		createTester("stats@tester.test", "Tester-Pass-1", Instant.now().plus(5, ChronoUnit.DAYS));
		var overview = body(get("/admin/stats/overview"), support);
		assertThat(overview.get("tenants").get("total").asLong()).isEqualTo(2);
		var withInternal = body(get("/admin/stats/overview").param("includeInternal", "true"), support);
		assertThat(withInternal.get("tenants").get("total").asLong()).isEqualTo(3);
		var cvs = body(get("/admin/stats/cvs").param("granularity", "week"), support);
		assertThat(cvs.get("series").size()).isGreaterThan(0);
	}

	// ---- helpers ---------------------------------------------------------------------------

	private PlatformAdmin admin(String email, String role, String password) {
		return mongo.insert(PlatformAdmin.builder().email(email).role(role).status(PlatformAdmin.STATUS_ACTIVE)
			.encryptedPassword(passwordEncoder.encode(password)).credentialVersion(0).createdAt(Instant.now()).build());
	}

	private String adminId(String email) {
		return mongo.getCollection("platform_admins").find(new Document("email", email)).first().getObjectId("_id").toHexString();
	}

	private ProductReference scaleProduct() {
		var price = new StripePrice();
		price.setStripePriceId(SCALE_PRICE);
		price.setCurrency("eur");
		price.setUnitAmount(19900L);
		price.setInterval("month");
		price.setIntervalCount(1);
		price.setActive(true);
		var product = new ProductReference();
		product.setStripeProductId(SCALE_PRODUCT);
		product.setName("Scale");
		product.setActive(true);
		product.setPrices(List.of(price));
		product.setFeatures(productProperties.getScale().getFeatures());
		return product;
	}

	private JsonNode createTester(String email, String password, Instant until) throws Exception {
		var request = json.createObjectNode().put("firstName", "John").put("lastName", "Doe").put("email", email)
			.put("password", password).put("productId", SCALE_PRODUCT).put("accessExpiresAt", until.toString());
		var response = mvc.perform(post("/admin/testers").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
			.content(request.toString())).andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
		return json.readTree(response.getContentAsString());
	}

	private int reactivate(String tenantId, String password, Instant until) throws Exception {
		var request = json.createObjectNode();
		if (password != null) request.put("password", password);
		if (until != null) request.put("accessExpiresAt", until.toString());
		return status(post("/admin/testers/" + tenantId + "/reactivate").contentType(MediaType.APPLICATION_JSON)
			.content(request.toString()), owner);
	}

	private JsonNode login(String email, String password) throws Exception {
		var response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"rawPassword\":\"" + password + "\"}")).andReturn().getResponse();
		var tree = json.readTree(response.getContentAsString());
		return tree.hasNonNull("data") ? tree.get("data") : tree;
	}

	private int status(MockHttpServletRequestBuilder request, String bearer) throws Exception {
		if (bearer != null) request.header("Authorization", bearer);
		return mvc.perform(request).andReturn().getResponse().getStatus();
	}

	private JsonNode body(MockHttpServletRequestBuilder request, String bearer) throws Exception {
		if (bearer != null) request.header("Authorization", bearer);
		var response = mvc.perform(request).andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isBetween(200, 299);
		return json.readTree(response.getContentAsString());
	}
}
