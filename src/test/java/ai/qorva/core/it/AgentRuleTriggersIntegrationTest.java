package ai.qorva.core.it;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRuleFiring;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.CandidateUpdateRequest;
import ai.qorva.core.dao.entity.PendingEmailNotification;
import ai.qorva.core.scheduler.AgentRuleScheduler;
import ai.qorva.core.service.CandidateUpdateEmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The triggers added on 2026-10-08, end to end: each is accepted by the API and the collection validator, fires on
 * what it watches (once per episode), and stays quiet otherwise. The scheduler's timer is pushed out of the way and
 * ticked by hand; "time passing" is simulated by moving the records' dates back and the rule's watermark with them.
 */
@TestPropertySource(properties = {"qorva.ai.agent.enabled=true", "qorva.ai.agent.rules.enabled=true",
	"qorva.ai.agent.rules.poll-delay-ms=3600000", "qorva.ai.agent.poll-delay-ms=3600000"})
class AgentRuleTriggersIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@MockitoBean
	private ChatModel chatModel;
	/** Nothing leaves the test by email. */
	@MockitoBean
	private CandidateUpdateEmailService candidateUpdateEmails;

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private AgentRuleScheduler scheduler;

	private TwoTenantFixture.SeededTenant a;
	private String owner;

	@BeforeEach
	void seed() {
		a = fixture.reset().a();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		mongo.remove(new Query(), AgentRule.class);
		mongo.remove(new Query(), AgentRuleFiring.class);
		mongo.remove(new Query(), AgentRun.class);
		mongo.remove(new Query(), PendingEmailNotification.class);
		mongo.remove(new Query(), CandidateUpdateRequest.class);
		when(chatModel.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Done.")))));
	}

	private JsonNode createRule(String body) throws Exception {
		var response = mvc.perform(post("/agent/rules").header("Authorization", owner).contentType(JSON).content(body))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
		return objectMapper.readTree(response.getContentAsString());
	}

	private int status(String body) throws Exception {
		return mvc.perform(post("/agent/rules").header("Authorization", owner).contentType(JSON).content(body))
			.andReturn().getResponse().getStatus();
	}

	/** The rule has been watching for a while: its window reaches back {@code by}. */
	private void startedAgo(JsonNode rule, Duration by) {
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(rule.path("id").asText()))),
			new Update().set("watermark", Date.from(Instant.now().minus(by))), "agent_rules");
	}

	private void tick() {
		mongo.updateMulti(new Query(), new Update().unset("nextCheckAt"), AgentRule.class);
		scheduler.tick();
	}

	private List<AgentRun> ruleRuns() {
		return mongo.find(Query.query(Criteria.where("origin").is(AgentRun.ORIGIN_RULE)), AgentRun.class);
	}

	private void awaitRuleRunsIdle() throws InterruptedException {
		var working = Query.query(Criteria.where("origin").is(AgentRun.ORIGIN_RULE)
			.and("status").in(AgentRun.STATUS_QUEUED, AgentRun.STATUS_RUNNING));
		var deadline = System.currentTimeMillis() + 20_000;
		while (mongo.exists(working, AgentRun.class)) {
			if (System.currentTimeMillis() > deadline) throw new AssertionError("rule runs did not finish");
			Thread.sleep(50);
		}
	}

	private void completeRuleRuns() throws InterruptedException {
		awaitRuleRunsIdle();
		mongo.updateMulti(new Query(), new Update().set("status", AgentRun.STATUS_COMPLETED), AgentRun.class);
	}

	private void set(String collection, String id, Map<String, Object> values) {
		var update = new Update();
		values.forEach((k, v) -> update.set(k, v instanceof Instant i ? Date.from(i) : v));
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(id))), update, collection);
	}

	private List<String> mentionedIds(AgentRun run) {
		return run.getMentions().stream().map(AgentRun.Mention::getId).toList();
	}

	@Test
	void everyNewTriggerIsAcceptedAndBadOnesAreRefused() throws Exception {
		createRule("""
			{"name":"Follow up","goalTemplate":"Draft a follow-up for {{candidates}}.","trigger":{"type":"REPORT_STATUS_IDLE","toStatuses":["CONTACTED"],"idleDays":7}}""");
		createRule("""
			{"name":"Refresh","goalTemplate":"Ask {{candidates}} to update their profile.","autoApproveProfileUpdates":true,
			 "trigger":{"type":"CV_OUTDATED","staleMonths":18}}""");
		createRule("""
			{"name":"Closed","goalTemplate":"Tell {{candidates}} the role is filled.","trigger":{"type":"JOB_CLOSED"}}""");
		createRule("""
			{"name":"Dupes","goalTemplate":"Compare {{candidates}}.","trigger":{"type":"DUPLICATE_FOUND","source":"ATS"}}""");
		createRule("""
			{"name":"Updated","goalTemplate":"Re-run matching for {{candidates}}.","trigger":{"type":"CANDIDATE_PROFILE_UPDATED"}}""");
		var reject = createRule("""
			{"name":"Reject","goalTemplate":"Move {{candidates}} to Rejected.","trigger":{"type":"CV_SCORED","recommendations":["reject"],"maxScore":40}}""");
		assertThat(reject.path("trigger").path("recommendations").get(0).asText()).isEqualTo("reject");
		assertThat(reject.path("trigger").path("maxScore").asInt()).isEqualTo(40);

		assertThat(status("""
			{"name":"x","goalTemplate":"x","trigger":{"type":"REPORT_STATUS_IDLE","idleDays":7}}""")).as("idle needs statuses").isEqualTo(400);
		assertThat(status("""
			{"name":"x","goalTemplate":"x","trigger":{"type":"REPORT_STATUS_IDLE","toStatuses":["NEW"],"idleDays":91}}""")).isEqualTo(400);
		assertThat(status("""
			{"name":"x","goalTemplate":"x","trigger":{"type":"CV_OUTDATED","staleMonths":7}}""")).isEqualTo(400);
		assertThat(status("""
			{"name":"x","goalTemplate":"x","trigger":{"type":"CV_SCORED","minScore":60,"maxScore":50}}""")).isEqualTo(400);
		assertThat(status("""
			{"name":"x","goalTemplate":"x","trigger":{"type":"CV_SCORED","recommendations":["maybe"]}}""")).isEqualTo(400);
		assertThat(status("""
			{"name":"x","goalTemplate":"x","autoApproveProfileUpdates":true,"autoApproveProfileUpdatesMax":26,"trigger":{"type":"CV_OUTDATED"}}""")).isEqualTo(400);
	}

	@Test
	void aVerdictRuleFiresOnlyForThatVerdictAndAgainWhenARescoreChangesIt() throws Exception {
		var verdict = "matchingReportDetails.decisionSummary.recommendation";
		set("matching_reports", a.reportIds().get(0), Map.of(verdict, "interview"));
		set("matching_reports", a.reportIds().get(1), Map.of(verdict, "reject"));
		set("matching_reports", a.reportIds().get(2), Map.of(verdict, "may_be"));
		createRule("""
			{"name":"Reject","goalTemplate":"Move {{candidates}} to Rejected.","trigger":{"type":"CV_SCORED","recommendations":["reject"]}}""");

		for (var id : a.reportIds()) set("matching_reports", id, Map.of("lastUpdatedAt", Instant.now()));
		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(mentionedIds(runs.getFirst())).contains(a.cvIds().get(1)).doesNotContain(a.cvIds().get(0), a.cvIds().get(2));

		// Re-scored to "reject": a new verdict for that pair, so it fires again.
		completeRuleRuns();
		set("matching_reports", a.reportIds().get(0), Map.of(verdict, "reject", "lastUpdatedAt", Instant.now()));
		tick();
		assertThat(ruleRuns()).hasSize(2);
	}

	@Test
	void anIdleRuleFiresOncePerIdlePeriodIncludingReportsNeverMoved() throws Exception {
		var rule = createRule("""
			{"name":"Follow up","goalTemplate":"Draft a follow-up for {{candidates}} on {{job}}.",
			 "trigger":{"type":"REPORT_STATUS_IDLE","toStatuses":["CONTACTED","NEW"],"idleDays":7}}""");
		startedAgo(rule, Duration.ofHours(1));
		var eightDaysAgo = Instant.now().minus(Duration.ofDays(7)).minus(Duration.ofMinutes(10));
		set("matching_reports", a.reportIds().get(0), Map.of("status", "CONTACTED", "statusChangedAt", eightDaysAgo));
		// Never moved out of New: it counts from its creation.
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(a.reportIds().get(1)))),
			new Update().set("status", "NEW").unset("statusChangedAt").set("createdAt", Date.from(eightDaysAgo)), "matching_reports");
		// Contacted 2 days ago: not idle yet.
		set("matching_reports", a.reportIds().get(2), Map.of("status", "CONTACTED", "statusChangedAt", Instant.now().minus(Duration.ofDays(2))));

		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(mentionedIds(runs.getFirst())).contains(a.cvIds().get(0), a.cvIds().get(1)).doesNotContain(a.cvIds().get(2));
		assertThat(runs.getFirst().getHistory().getFirst().getText()).contains("has been CONTACTED for 7 days");

		// The same idle period never fires twice.
		completeRuleRuns();
		tick();
		assertThat(ruleRuns()).hasSize(1);
	}

	@Test
	void anOutdatedRuleFiresForCvsCrossingTheAgeAndCanAskThemToUpdatePreApproved() throws Exception {
		var cv = a.cvIds().get(0);
		var other = a.cvIds().get(1);
		when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
			var last = inv.<Prompt>getArgument(0).getInstructions().getLast();
			if (last.getMessageType() == MessageType.TOOL) {
				return new ChatResponse(List.of(new Generation(new AssistantMessage("Asked them to update."))));
			}
			return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(
				new AssistantMessage.ToolCall("c1", "function", "request_profile_update", "{\"cvIds\":[\"" + cv + "\"]}"))))));
		});
		var rule = createRule("""
			{"name":"Refresh","goalTemplate":"Ask {{candidates}} to update their profile.","autoApproveProfileUpdates":true,
			 "autoApproveProfileUpdatesMax":5,"trigger":{"type":"CV_OUTDATED","staleMonths":18}}""");
		startedAgo(rule, Duration.ofHours(1));
		var crossed = Instant.now().atOffset(ZoneOffset.UTC).minusMonths(18).minusMinutes(10).toInstant();
		set("cvs", cv, Map.of("contentDate", crossed, "contentDateSource", "WORK_HISTORY",
			"personalInformation.contact.email", "olivia.candidate@example.test"));
		// Same age, but a request is already in progress: left out.
		set("cvs", other, Map.of("contentDate", crossed, "contentDateSource", "WORK_HISTORY"));
		mongo.insert(new Document("tenantId", new ObjectId(a.tenantId())).append("cvId", other).append("status", "SENT")
			.append("tokenHash", "h-" + other).append("candidateEmail", "x@example.test").append("sentAt", new Date()), "candidate_update_requests");

		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(mentionedIds(runs.getFirst())).containsExactly(cv);

		awaitRuleRunsIdle();
		var run = mongo.findById(new ObjectId(runs.getFirst().getId()), AgentRun.class);
		assertThat(run.getStatus()).as(run.getFailureReason()).isEqualTo(AgentRun.STATUS_COMPLETED);
		assertThat(run.getSteps()).anyMatch(s -> "request_profile_update".equals(s.getTool()) && Boolean.TRUE.equals(s.getAutoApproved()));
		assertThat(mongo.exists(Query.query(Criteria.where("cvId").is(cv).and("status").is("SENT")), CandidateUpdateRequest.class)).isTrue();
		verify(candidateUpdateEmails, times(1)).sendUpdateInvitation(eq("olivia.candidate@example.test"), any(), any(), anyString(),
			any(), any(), any());
	}

	@Test
	void aClosedJobFiresWithTheCandidatesStillWaitingOnIt() throws Exception {
		createRule("""
			{"name":"Closed","goalTemplate":"Tell {{candidates}} the role {{job}} is filled.","trigger":{"type":"JOB_CLOSED"}}""");
		set("matching_reports", a.reportIds().get(0), Map.of("status", "CONTACTED"));
		set("matching_reports", a.reportIds().get(1), Map.of("status", "HIRED"));

		var response = mvc.perform(patch("/jobs/" + a.jobId()).header("Authorization", owner).contentType(JSON)
			.content("{\"status\":\"closed\"}")).andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		assertThat(mongo.findById(new ObjectId(a.jobId()), Document.class, "job_posts").get("statusChangedAt")).isNotNull();

		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(mentionedIds(runs.getFirst())).contains(a.jobId(), a.cvIds().get(0)).doesNotContain(a.cvIds().get(1));
		completeRuleRuns();
		tick();
		assertThat(ruleRuns()).hasSize(1);
	}

	@Test
	void aNewCvWithAnExistingContactFiresWithBothCandidates() throws Exception {
		var rule = createRule("""
			{"name":"Dupes","goalTemplate":"Compare {{candidates}}.","trigger":{"type":"DUPLICATE_FOUND"}}""");
		startedAgo(rule, Duration.ofMinutes(10));
		var older = a.cvIds().get(0);
		var newer = a.cvIds().get(1);
		set("cvs", older, Map.of("contactKeys.email", "same@example.test", "createdAt", Instant.now().minus(Duration.ofDays(30))));
		set("cvs", newer, Map.of("contactKeys.email", "same@example.test", "createdAt", Instant.now().minus(Duration.ofMinutes(3))));

		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(mentionedIds(runs.getFirst())).containsExactly(newer, older);
		assertThat(runs.getFirst().getHistory().getFirst().getText()).contains("same email");
	}

	@Test
	void aCompletedProfileUpdateFires() throws Exception {
		createRule("""
			{"name":"Updated","goalTemplate":"Re-run matching for {{candidates}}.","trigger":{"type":"CANDIDATE_PROFILE_UPDATED"}}""");
		var cv = a.cvIds().get(2);
		mongo.insert(new Document("tenantId", new ObjectId(a.tenantId())).append("cvId", cv).append("status", "COMPLETED")
			.append("tokenHash", "h-done").append("candidateEmail", "c@example.test").append("sentAt", new Date())
			.append("completedAt", new Date()), "candidate_update_requests");

		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(mentionedIds(runs.getFirst())).containsExactly(cv);
		assertThat(runs.getFirst().getHistory().getFirst().getText()).contains("updated their profile");
	}

	@Test
	void aCvTheCandidateUploadedThroughAnUpdateIsNotANewCandidate() throws Exception {
		var rule = createRule("""
			{"name":"Tag new","goalTemplate":"Tag {{candidates}}.","trigger":{"type":"CV_ADDED"}}""");
		startedAgo(rule, Duration.ofMinutes(10));
		set("cvs", a.cvIds().get(0), Map.of("createdAt", Instant.now().minus(Duration.ofMinutes(3)), "origin", "CANDIDATE_UPDATE"));
		set("cvs", a.cvIds().get(1), Map.of("createdAt", Instant.now().minus(Duration.ofMinutes(3))));

		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(mentionedIds(runs.getFirst())).containsExactly(a.cvIds().get(1));
	}
}
