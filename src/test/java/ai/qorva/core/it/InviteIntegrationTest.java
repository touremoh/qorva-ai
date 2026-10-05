package ai.qorva.core.it;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.migrations.V2026100505ClearSentEmailPayloads;
import ai.qorva.core.scheduler.PendingEmailNotificationScheduler;
import ai.qorva.core.service.AccountCreationNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invites by link: nothing secret is queued, the link is minted when the email goes out (or the Microsoft wording
 * when the company requires it), a re-send revokes the previous link, and the first sign-in ends the pending state.
 */
class InviteIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;
	private static final String INVITEE = "new.recruiter@a.qorva.test";

	@MockitoBean
	private AccountCreationNotificationService accountCreation;
	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;
	@Autowired
	private PendingEmailNotificationScheduler scheduler;
	@Autowired
	private ObjectMapper objectMapper;

	private TwoTenantFixture.SeededTenant a;
	private TwoTenantFixture.SeededTenant b;
	private String owner;

	@BeforeEach
	void seed() {
		var seeded = fixture.reset();
		a = seeded.a();
		b = seeded.b();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
	}

	private String invite() throws Exception {
		var body = mvc.perform(post("/users/invite").header("Authorization", owner).contentType(JSON).content("""
				{"firstName":"Nora","lastName":"New","email":"%s","communicationLanguage":"en","authorities":[]}""".formatted(INVITEE)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.invitePending").value(true))
			.andExpect(jsonPath("$.invitedAt").exists())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).path("id").asText();
	}

	private List<Document> queued() {
		return mongo.find(Query.query(Criteria.where("notificationType").is("USER_ADDED")), Document.class, "pending_email_notifications");
	}

	/** Sends what is queued and returns the payload the invite email was built from. */
	@SuppressWarnings("unchecked")
	private Map<String, String> sendInvite() throws Exception {
		clearInvocations(accountCreation);
		scheduler.processPendingNotifications();
		ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
		verify(accountCreation).sendUserAdded(any(), payload.capture(), anyString());
		return payload.getValue();
	}

	private void cooldownOver() {
		mongo.updateMulti(new Query(), new Update().set("createdAt", Date.from(java.time.Instant.now().minusSeconds(300))), "pending_email_notifications");
	}

	private String tokenOf(String url) {
		return url.substring(url.indexOf("token=") + 6);
	}

	@Test
	void anInviteQueuesNoSecretAndItsLinkSetsThePasswordOnce() throws Exception {
		var id = invite();
		var row = queued().getFirst();
		assertThat(row.get("payload", Document.class)).containsOnlyKeys("companyName");

		var payload = sendInvite();
		assertThat(payload).containsKey("setPasswordUrl").doesNotContainKey("temporaryPassword").doesNotContainKey("sso");
		var token = tokenOf(payload.get("setPasswordUrl"));
		// Sent: the queue keeps nothing of it.
		assertThat(queued().getFirst()).doesNotContainKey("payload").containsEntry("status", "SENT");

		mvc.perform(post("/auth/password/set").contentType(JSON).content("{\"token\":\"%s\",\"newPassword\":\"Brand-New-Pass-9\"}".formatted(token)))
			.andExpect(status().isOk());
		assertThat(mongo.findById(new ObjectId(id), User.class).getInvitePending()).isFalse();
		mvc.perform(post("/auth/login").contentType(JSON).content("{\"email\":\"%s\",\"rawPassword\":\"Brand-New-Pass-9\"}".formatted(INVITEE)))
			.andExpect(status().isOk());
	}

	@Test
	void aResendRevokesThePreviousLinkAndRespectsTheCooldown() throws Exception {
		var id = invite();
		var firstToken = tokenOf(sendInvite().get("setPasswordUrl"));

		mvc.perform(post("/users/" + id + "/invite/resend").header("Authorization", owner))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.errorCode").value("error.user.invite_resend_too_soon"));

		cooldownOver();
		mvc.perform(post("/users/" + id + "/invite/resend").header("Authorization", owner)).andExpect(status().isAccepted());
		var secondToken = tokenOf(sendInvite().get("setPasswordUrl"));

		mvc.perform(post("/auth/password/set").contentType(JSON).content("{\"token\":\"%s\",\"newPassword\":\"Brand-New-Pass-9\"}".formatted(firstToken)))
			.andExpect(status().isConflict());
		mvc.perform(post("/auth/password/set").contentType(JSON).content("{\"token\":\"%s\",\"newPassword\":\"Brand-New-Pass-9\"}".formatted(secondToken)))
			.andExpect(status().isOk());
	}

	@Test
	void onlyAPendingInviteOfTheTenantCanBeResentByAnAdmin() throws Exception {
		var id = invite();
		cooldownOver();
		mvc.perform(post("/users/" + id + "/invite/resend").header("Authorization", fixture.bearer(a.viewerEmail(), a.tenantId())))
			.andExpect(status().isForbidden());
		mvc.perform(post("/users/" + b.viewerId() + "/invite/resend").header("Authorization", owner))
			.andExpect(status().isNotFound());
		mvc.perform(post("/users/" + a.viewerId() + "/invite/resend").header("Authorization", owner))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("error.user.invite_not_pending"));
	}

	@Test
	void theFirstPasswordSignInEndsThePendingInvite() throws Exception {
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(a.viewerId()))), Update.update("invitePending", true), User.class);

		mvc.perform(post("/auth/login").contentType(JSON)
				.content("{\"email\":\"%s\",\"rawPassword\":\"%s\"}".formatted(a.viewerEmail(), TwoTenantFixture.PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.user.invitePending").value(false));
		assertThat(mongo.findById(new ObjectId(a.viewerId()), User.class).getInvitePending()).isFalse();
	}

	@Test
	void aCompanyThatRequiresMicrosoftSignInInvitesToSignInWithMicrosoft() throws Exception {
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(a.tenantId()))), Update.update("ssoRequired", true), "tenants");
		invite();

		var payload = sendInvite();

		assertThat(payload).containsEntry("sso", "true").containsEntry("signInUrl", "http://localhost:5173/login")
			.doesNotContainKey("setPasswordUrl");
	}

	@Test
	void theMigrationClearsWhatProcessedEmailsKept() {
		mongo.insert(new Document("userId", a.ownerId()).append("notificationType", "USER_ADDED").append("status", "SENT")
			.append("languageCode", "en").append("attempts", 1).append("maxAttempts", 3).append("createdAt", new Date())
			.append("payload", new Document("temporaryPassword", "Old-Secret-1")), "pending_email_notifications");
		mongo.insert(new Document("userId", a.ownerId()).append("notificationType", "USER_ADDED").append("status", "PENDING")
			.append("languageCode", "en").append("attempts", 0).append("maxAttempts", 3).append("createdAt", new Date())
			.append("payload", new Document("companyName", "Acme")), "pending_email_notifications");

		new V2026100505ClearSentEmailPayloads().execute(mongo.getDb());

		var rows = mongo.find(new Query(), Document.class, "pending_email_notifications");
		assertThat(rows).filteredOn(r -> "SENT".equals(r.getString("status"))).allSatisfy(r -> assertThat(r).doesNotContainKey("payload"));
		assertThat(rows).filteredOn(r -> "PENDING".equals(r.getString("status"))).allSatisfy(r -> assertThat(r).containsKey("payload"));
	}
}
