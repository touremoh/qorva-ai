package ai.qorva.core.it;

import ai.qorva.core.dao.entity.UsageMonitoring;
import ai.qorva.core.dto.PendingEmailNotificationDTO;
import ai.qorva.core.enums.EmailNotificationType;
import ai.qorva.core.scheduler.BackgroundJobWorker;
import ai.qorva.core.scheduler.PendingEmailNotificationScheduler;
import ai.qorva.core.scheduler.UsageMonitoringScheduler;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.CandidateUpdateEmailService;
import ai.qorva.core.service.EmailNotificationDispatcher;
import ai.qorva.core.service.PendingEmailNotificationService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Background work runs off the request thread, where no JWT sets a tenant. Each worker must touch
 * only the tenant its unit of work belongs to, and must run that work under that tenant's context.
 */
class WorkerTenantIsolationIntegrationTest extends AbstractIntegrationTest {

	@MockitoBean
	private CandidateUpdateEmailService candidateUpdateEmailService;
	@MockitoBean
	private EmailNotificationDispatcher emailNotificationDispatcher;

	@Autowired private TwoTenantFixture fixture;
	@Autowired private MongoTemplate mongo;
	@Autowired private BackgroundJobWorker backgroundJobWorker;
	@Autowired private UsageMonitoringScheduler usageMonitoringScheduler;
	@Autowired private PendingEmailNotificationScheduler pendingEmailNotificationScheduler;
	@Autowired private PendingEmailNotificationService pendingEmailNotificationService;

	private TwoTenantFixture.SeededTenant a;
	private TwoTenantFixture.SeededTenant b;

	/** Recipient → the tenant context the invitation was sent under. */
	private final Map<String, String> invitations = new ConcurrentHashMap<>();

	@BeforeEach
	void seed() throws Exception {
		var seeded = fixture.reset();
		a = seeded.a();
		b = seeded.b();
		invitations.clear();
		doAnswer(call -> {
			invitations.put(call.getArgument(0), String.valueOf(TenantContextHolder.getTenantId()));
			return null;
		}).when(candidateUpdateEmailService)
			.sendUpdateInvitation(anyString(), any(), any(), any(), any(), any(), any());
	}

	@Test
	void candidateUpdateCampaign_reachesOnlyItsTenantsCandidates_underThatTenant() throws Exception {
		var bBefore = fixture.fingerprint(b.tenantId());
		submitCampaign(a);

		backgroundJobWorker.poll();

		var aEmails = emailsOfTenant(a.tenantId());
		assertThat(invitations).isNotEmpty();
		assertThat(aEmails).containsAll(invitations.keySet());
		assertThat(invitations.values()).as("tenant context during the send").containsOnly(a.tenantId());
		assertThat(requestTenants()).containsOnly(a.tenantId());
		assertThat(jobStatus(a.tenantId())).isEqualTo("COMPLETED");
		assertThat(fixture.fingerprint(b.tenantId())).as("tenant B untouched").isEqualTo(bBefore);
	}

	@Test
	void usagePeriod_isOpenedOnlyForTheTenantThatLacksOne() {
		mongo.remove(Query.query(Criteria.where("tenantId").is(new ObjectId(a.tenantId()))), UsageMonitoring.class);
		var bBefore = fixture.fingerprint(b.tenantId());

		usageMonitoringScheduler.initializeUsageMonitoringPeriods();

		assertThat(mongo.count(Query.query(Criteria.where("tenantId").is(new ObjectId(a.tenantId()))), UsageMonitoring.class))
			.isEqualTo(1);
		assertThat(fixture.fingerprint(b.tenantId())).isEqualTo(bBefore);
	}

	@Test
	void pendingEmails_areDispatchedUnderTheirOwnTenant() throws Exception {
		pendingEmailNotificationService.createPending(a.tenantId(), a.ownerId(), EmailNotificationType.SUBSCRIPTION_WELCOME, "en");
		pendingEmailNotificationService.createPending(b.tenantId(), b.ownerId(), EmailNotificationType.SUBSCRIPTION_WELCOME, "en");
		var dispatched = new ArrayList<List<String>>();
		doAnswer(call -> {
			PendingEmailNotificationDTO notification = call.getArgument(0);
			dispatched.add(List.of(notification.getTenantId(), String.valueOf(TenantContextHolder.getTenantId())));
			return null;
		}).when(emailNotificationDispatcher).dispatch(any());

		pendingEmailNotificationScheduler.processPendingNotifications();

		assertThat(dispatched).hasSize(2)
			.allSatisfy(pair -> assertThat(pair.get(1)).as("context of %s", pair.get(0)).isEqualTo(pair.get(0)));
		assertThat(TenantContextHolder.getTenantId()).as("context restored after the run").isNull();
	}

	private void submitCampaign(TwoTenantFixture.SeededTenant tenant) throws Exception {
		mvc.perform(post("/library-quality/jobs").header("Authorization", fixture.bearer(tenant.ownerEmail(), tenant.tenantId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"CANDIDATE_UPDATE_CAMPAIGN\",\"issueKey\":\"OUTDATED\",\"dryRun\":false,\"language\":\"en\"}"))
			.andReturn();
	}

	private List<String> emailsOfTenant(String tenantId) {
		return mongo.getCollection("cvs").find(new Document("tenantId", new ObjectId(tenantId)))
			.map(doc -> doc.getEmbedded(List.of("personalInformation", "contact", "email"), String.class))
			.into(new ArrayList<>());
	}

	private List<String> requestTenants() {
		return mongo.getCollection("candidate_update_requests").find()
			.map(doc -> String.valueOf(doc.get("tenantId")))
			.into(new ArrayList<>());
	}

	private String jobStatus(String tenantId) {
		var job = mongo.getCollection("background_jobs").find(new Document("tenantId", new ObjectId(tenantId))).first();
		return job == null ? null : job.getString("status");
	}
}
