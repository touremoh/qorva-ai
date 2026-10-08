package ai.qorva.core.it;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRuleFiring;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.PendingEmailNotification;
import ai.qorva.core.scheduler.AgentRuleScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Copilot standing rules end to end: the API (ownership, team scope, validation, tenant isolation) and the
 * scheduler against real Mongo — triggers, the firing ledger, caps, pauses and the approval digest. The
 * scheduler's own timer is pushed out of the test's way; each test ticks it by hand.
 */
@TestPropertySource(properties = {"qorva.ai.agent.enabled=true", "qorva.ai.agent.rules.enabled=true",
	"qorva.ai.agent.rules.poll-delay-ms=3600000", "qorva.ai.agent.poll-delay-ms=3600000"})
class AgentRuleIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@MockitoBean
	private ChatModel chatModel;

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private AgentRuleScheduler scheduler;

	private TwoTenantFixture.SeededTenant a;
	private TwoTenantFixture.SeededTenant b;
	private String owner;

	@BeforeEach
	void seed() {
		var seeded = fixture.reset();
		a = seeded.a();
		b = seeded.b();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		mongo.remove(new Query(), AgentRule.class);
		mongo.remove(new Query(), AgentRuleFiring.class);
		mongo.remove(new Query(), AgentRun.class);
		mongo.remove(new Query(), PendingEmailNotification.class);
		when(chatModel.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Done.")))));
	}

	private JsonNode json(String body) throws Exception {
		return objectMapper.readTree(body);
	}

	private JsonNode createRule(String token, String body) throws Exception {
		var response = mvc.perform(post("/agent/rules").header("Authorization", token).contentType(JSON).content(body))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
		return json(response.getContentAsString());
	}

	private String scoredRule(int cap) {
		return """
			{"name":"Invite strong matches","goalTemplate":"Draft an interview invitation for {{candidates}} for {{job}}.",
			 "dailyRunCap":%d,"trigger":{"type":"CV_SCORED","jobPostId":"%s","minScore":70,"recommendedOnly":true}}"""
			.formatted(cap, a.jobId());
	}

	/** Makes every rule due again and runs one scheduler tick. */
	private void tick() {
		mongo.updateMulti(new Query(), new Update().unset("nextCheckAt"), AgentRule.class);
		scheduler.tick();
	}

	private void touch(String collection, String field, List<String> ids) {
		mongo.updateMulti(Query.query(Criteria.where("_id").in(ids.stream().map(ObjectId::new).toList())),
			new Update().set(field, Instant.now()), collection);
	}

	/** CVs created {@code age} ago: new CV triggers only look at CVs older than their settle time. */
	private void touchSettled(List<String> cvIds, Duration age) {
		mongo.updateMulti(Query.query(Criteria.where("_id").in(cvIds.stream().map(ObjectId::new).toList())),
			new Update().set("createdAt", Instant.now().minus(age)), "cvs");
	}

	private void backdateWatermark(String ruleId, Duration by) {
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(ruleId))),
			new Update().set("watermark", Instant.now().minus(by)), "agent_rules");
	}

	private List<AgentRun> ruleRuns() {
		return mongo.find(Query.query(Criteria.where("origin").is(AgentRun.ORIGIN_RULE)), AgentRun.class);
	}

	/** The worker executes rule runs in the background; wait until it is done before rewriting their status. */
	private void awaitRuleRunsIdle() throws InterruptedException {
		var working = Query.query(Criteria.where("origin").is(AgentRun.ORIGIN_RULE)
			.and("status").in(AgentRun.STATUS_QUEUED, AgentRun.STATUS_RUNNING));
		var deadline = System.currentTimeMillis() + 20_000;
		while (mongo.exists(working, AgentRun.class)) {
			if (System.currentTimeMillis() > deadline) throw new AssertionError("rule runs did not finish");
			Thread.sleep(50);
		}
	}

	private AgentRule rule(String id) {
		return mongo.findById(new ObjectId(id), AgentRule.class);
	}

	private void grantViewer(String action) {
		mongo.updateFirst(Query.query(Criteria.where("email").is(a.viewerEmail())),
			new Update().push("authorities", new Document("role", "ACCOUNT_MANAGER").append("action", action).append("permission", "ALLOWED")),
			"users");
	}

	@Test
	void anOwnerManagesTheirRulesAndNobodyElseEditsThem() throws Exception {
		var rule = createRule(owner, scoredRule(5));
		var id = rule.path("id").asText();
		assertThat(rule.path("status").asText()).isEqualTo("ACTIVE");
		assertThat(rule.path("trigger").path("jobTitle").asText()).isNotBlank();
		assertThat(rule.path("canEdit").asBoolean()).isTrue();

		// Another tenant can't see it.
		var other = fixture.bearer(b.ownerEmail(), b.tenantId());
		assertThat(mvc.perform(get("/agent/rules/" + id).header("Authorization", other)).andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(mvc.perform(delete("/agent/rules/" + id).header("Authorization", other)).andReturn().getResponse().getStatus()).isEqualTo(404);

		// A colleague without USE_AGENT gets nothing; with it, still not someone else's rule.
		var viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
		assertThat(mvc.perform(get("/agent/rules").header("Authorization", viewer)).andReturn().getResponse().getStatus()).isEqualTo(403);
		grantViewer("USE_AGENT");
		viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
		assertThat(json(mvc.perform(get("/agent/rules").header("Authorization", viewer)).andReturn().getResponse().getContentAsString())).isEmpty();
		assertThat(mvc.perform(put("/agent/rules/" + id).header("Authorization", viewer).contentType(JSON).content(scoredRule(3)))
			.andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(mvc.perform(post("/agent/rules/" + id + "/pause").header("Authorization", viewer)).andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(mvc.perform(get("/agent/rules?scope=team").header("Authorization", viewer)).andReturn().getResponse().getStatus()).isEqualTo(403);

		// The owner (who manages users) sees the team, pauses and resumes, edits and deletes.
		assertThat(json(mvc.perform(get("/agent/rules?scope=team").header("Authorization", owner)).andReturn().getResponse().getContentAsString())).hasSize(1);
		var paused = json(mvc.perform(post("/agent/rules/" + id + "/pause").header("Authorization", owner)).andReturn().getResponse().getContentAsString());
		assertThat(paused.path("pausedReason").asText()).isEqualTo("MANUAL");
		var resumed = json(mvc.perform(post("/agent/rules/" + id + "/resume").header("Authorization", owner)).andReturn().getResponse().getContentAsString());
		assertThat(resumed.path("status").asText()).isEqualTo("ACTIVE");
		var updated = json(mvc.perform(put("/agent/rules/" + id).header("Authorization", owner).contentType(JSON).content(scoredRule(3)))
			.andReturn().getResponse().getContentAsString());
		assertThat(updated.path("dailyRunCap").asInt()).isEqualTo(3);
		assertThat(mvc.perform(delete("/agent/rules/" + id).header("Authorization", owner)).andReturn().getResponse().getStatus()).isEqualTo(204);
		assertThat(mongo.count(new Query(), AgentRule.class)).isZero();
	}

	@Test
	void anInvalidRuleIsRefused() throws Exception {
		for (var body : List.of(
			"{\"name\":\"x\",\"goalTemplate\":\"g\",\"trigger\":{\"type\":\"NOPE\"}}",
			"{\"name\":\"x\",\"goalTemplate\":\"g\",\"trigger\":{\"type\":\"SCHEDULE\",\"hour\":25}}",
			"{\"name\":\"x\",\"goalTemplate\":\"g\",\"trigger\":{\"type\":\"SCHEDULE\",\"hour\":9,\"zoneId\":\"Mars/Base\"}}",
			"{\"name\":\"x\",\"goalTemplate\":\"g\",\"dailyRunCap\":1000,\"trigger\":{\"type\":\"CV_ADDED\"}}",
			"{\"name\":\"\",\"goalTemplate\":\"g\",\"trigger\":{\"type\":\"CV_ADDED\"}}",
			"{\"name\":\"x\",\"goalTemplate\":\"g\",\"trigger\":{\"type\":\"CV_SCORED\",\"jobPostId\":\"" + b.jobId() + "\"}}")) {
			var response = mvc.perform(post("/agent/rules").header("Authorization", owner).contentType(JSON).content(body)).andReturn().getResponse();
			assertThat(response.getStatus()).as(body).isIn(400, 404);
		}
		assertThat(mongo.count(new Query(), AgentRule.class)).isZero();
	}

	@Test
	void aScoredRuleFiresOncePerCandidateAndJobAndOnlyForWhatMatches() throws Exception {
		var id = createRule(owner, scoredRule(10)).path("id").asText();

		tick();
		assertThat(ruleRuns()).as("seeded reports predate the rule").isEmpty();

		// Re-scored now: 82 (interview), 64 (may_be), 41 (reject) — only the first matches.
		touch("matching_reports", "lastUpdatedAt", a.reportIds());
		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		var run = runs.getFirst();
		assertThat(run.getRuleId()).isEqualTo(id);
		assertThat(run.getUserEmail()).isEqualTo(a.ownerEmail());
		assertThat(run.getMentions()).extracting(AgentRun.Mention::getId).containsExactlyInAnyOrder(a.cvIds().getFirst(), a.jobId());
		assertThat(run.getGoal()).doesNotContain("{{");
		assertThat(run.getHistory().getFirst().getText()).contains("cvId=" + a.cvIds().getFirst()).contains("score 82");

		// Re-scored again: the pair already fired.
		awaitRuleRunsIdle();
		mongo.updateMulti(new Query(), new Update().set("status", AgentRun.STATUS_COMPLETED), AgentRun.class);
		touch("matching_reports", "lastUpdatedAt", a.reportIds());
		tick();
		assertThat(ruleRuns()).hasSize(1);
		assertThat(rule(id).getRunsToday()).isEqualTo(1);
	}

	@Test
	void newCvsAreBatchedAndTheDailyCapSkipsTheRest() throws Exception {
		var id = createRule(owner, """
			{"name":"Tag new CVs","goalTemplate":"Tag {{count}} new candidates.","dailyRunCap":1,"trigger":{"type":"CV_ADDED"}}""")
			.path("id").asText();
		// New CVs are looked at once settled (a minute old): start the rule earlier and age the CVs past that.
		backdateWatermark(id, Duration.ofMinutes(5));

		touchSettled(a.cvIds().subList(0, 2), Duration.ofMinutes(3));
		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(runs.getFirst().getMentions()).hasSize(2);
		assertThat(runs.getFirst().getGoal()).isEqualTo("Tag 2 new candidates.");

		awaitRuleRunsIdle();
		mongo.updateMulti(new Query(), new Update().set("status", AgentRun.STATUS_COMPLETED), AgentRun.class);
		touchSettled(a.cvIds().subList(2, 3), Duration.ofMinutes(2));
		tick();
		assertThat(ruleRuns()).hasSize(1);
		assertThat(rule(id).getSkippedToday()).isEqualTo(1);
	}

	@Test
	void aRuleRunDoesNotBlockItsOwnersChat() throws Exception {
		createRule(owner, scoredRule(10));
		touch("matching_reports", "lastUpdatedAt", a.reportIds());
		tick();
		awaitRuleRunsIdle();
		mongo.updateMulti(Query.query(Criteria.where("origin").is(AgentRun.ORIGIN_RULE)),
			new Update().set("status", AgentRun.STATUS_AWAITING_APPROVAL), AgentRun.class);

		var response = mvc.perform(post("/agent/runs").header("Authorization", owner).contentType(JSON)
			.content("{\"goal\":\"How many CVs?\"}")).andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(202);
	}

	@Test
	void aRulePausesWhenItsOwnerLosesCopilotOrTheJobGoes() throws Exception {
		grantViewer("USE_AGENT");
		var viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
		var viewerRule = createRule(viewer, "{\"name\":\"v\",\"goalTemplate\":\"g\",\"trigger\":{\"type\":\"CV_ADDED\"}}").path("id").asText();
		var ownerRule = createRule(owner, scoredRule(10)).path("id").asText();

		mongo.updateFirst(Query.query(Criteria.where("email").is(a.viewerEmail())),
			new Update().pull("authorities", new Document("action", "USE_AGENT")), "users");
		mongo.remove(Query.query(Criteria.where("_id").is(new ObjectId(a.jobId()))), "job_posts");
		tick();

		assertThat(rule(viewerRule).getPausedReason()).isEqualTo(AgentRule.PAUSED_OWNER_UNAVAILABLE);
		assertThat(rule(ownerRule).getPausedReason()).isEqualTo(AgentRule.PAUSED_JOB_DELETED);
		assertThat(ruleRuns()).isEmpty();
	}

	@Test
	void aRulePausesWithoutRunsLeftAndResumesWhenTheyAreBack() throws Exception {
		var id = createRule(owner, "{\"name\":\"t\",\"goalTemplate\":\"g\",\"trigger\":{\"type\":\"CV_ADDED\"}}").path("id").asText();
		var period = Query.query(Criteria.where("tenantId").is(new ObjectId(a.tenantId())));
		mongo.updateMulti(period, new Update().set("features.agentRuns.consumed", 500).set("features.agentRuns.limit", 500), "usage_monitoring");
		tick();
		assertThat(rule(id).getPausedReason()).isEqualTo(AgentRule.PAUSED_QUOTA);

		mongo.updateMulti(period, new Update().set("features.agentRuns.consumed", 0), "usage_monitoring");
		tick();
		assertThat(rule(id).getStatus()).isEqualTo(AgentRule.STATUS_ACTIVE);
		assertThat(rule(id).getPausedReason()).isNull();
	}

	@Test
	void ownersAreEmailedAtMostOncePerWindowAboutRunsWaitingForThem() throws Exception {
		createRule(owner, scoredRule(10));
		touch("matching_reports", "lastUpdatedAt", a.reportIds());
		tick();
		awaitRuleRunsIdle();
		mongo.updateMulti(Query.query(Criteria.where("origin").is(AgentRun.ORIGIN_RULE)),
			new Update().set("status", AgentRun.STATUS_AWAITING_APPROVAL).unset("notifiedAt"), AgentRun.class);

		tick();
		var digests = Query.query(Criteria.where("notificationType").is("AGENT_APPROVAL_DIGEST"));
		assertThat(mongo.find(digests, PendingEmailNotification.class)).hasSize(1)
			.allSatisfy(n -> assertThat(n.getUserId()).isEqualTo(a.ownerId()));
		assertThat(ruleRuns()).allSatisfy(r -> assertThat(r.getNotifiedAt()).isNotNull());

		// Paused again within the hour: no second email yet.
		mongo.updateMulti(Query.query(Criteria.where("origin").is(AgentRun.ORIGIN_RULE)), new Update().unset("notifiedAt"), AgentRun.class);
		tick();
		assertThat(mongo.count(digests, PendingEmailNotification.class)).isEqualTo(1);
	}

	/** Every job matched and up to date, so only what the test does makes one stale. */
	private void everyJobMatched() {
		mongo.updateMulti(new Query(), new Update().set("matchingReportsNeeded", false).unset("matchingStaleReason")
			.unset("matchingStaleAt").set("lastMatchedAt", new java.util.Date()), "job_posts");
	}

	private void editJob(String jobId, String title) throws Exception {
		var response = mvc.perform(put("/jobs/" + jobId).header("Authorization", owner).contentType(JSON)
			.content("{\"title\":\"%s\",\"description\":\"<p>Updated.</p>\",\"status\":\"open\"}".formatted(title)))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
	}

	@Test
	void aChangedJobFiresItsRuleOncePerEpisodeWithMatchingPreApproved() throws Exception {
		everyJobMatched();
		var rule = createRule(owner, """
			{"name":"Re-match changed jobs","goalTemplate":"Run matching for {{job}} with the top 5 candidates.",
			 "autoApproveMatching":true,"autoApproveMaxActions":30,
			 "trigger":{"type":"JOB_NEEDS_MATCHING","staleReasons":["JOB_CHANGED"]}}""");
		assertThat(rule.path("autoApproveMatching").asBoolean()).isTrue();
		assertThat(rule.path("autoApproveMaxActions").asInt()).isEqualTo(30);
		assertThat(rule.path("trigger").path("staleReasons")).hasSize(1);

		tick();
		assertThat(ruleRuns()).isEmpty();

		editJob(a.jobId(), "Senior Backend Engineer (Java)");
		tick();

		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(runs.getFirst().getAutoApproveMaxActions()).isEqualTo(30);
		assertThat(runs.getFirst().getGoal()).endsWith("with the top 5 candidates.");
		assertThat(runs.getFirst().getMentions()).extracting(AgentRun.Mention::getId).containsExactly(a.jobId());

		// Same episode: never again, even before the job is matched.
		awaitRuleRunsIdle();
		tick();
		assertThat(ruleRuns()).hasSize(1);
	}

	@Test
	void aJobRuleFiresOnlyForTheReasonsItWatches() throws Exception {
		everyJobMatched();
		createRule(owner, """
			{"name":"New jobs","goalTemplate":"Run matching for {{job}}.",
			 "trigger":{"type":"JOB_NEEDS_MATCHING","staleReasons":["NEVER_RUN"]}}""");

		editJob(a.jobId(), "Senior Backend Engineer (Java)");
		tick();
		assertThat(ruleRuns()).isEmpty();

		var invalid = mvc.perform(post("/agent/rules").header("Authorization", owner).contentType(JSON).content("""
			{"name":"Bad","goalTemplate":"x","trigger":{"type":"JOB_NEEDS_MATCHING","staleReasons":["SOMETIMES"]}}"""))
			.andReturn().getResponse();
		assertThat(invalid.getStatus()).isEqualTo(400);
	}

	private void setStatus(String reportId, String status) throws Exception {
		var response = mvc.perform(patch("/matching-reports/" + reportId + "/status").header("Authorization", owner)
			.contentType(JSON).content("{\"status\":\"" + status + "\"}")).andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
	}

	@Test
	void aStatusRuleFiresOncePerChangeOnlyForTheStatusesItWatchesAndNeverForCopilotsOwnChanges() throws Exception {
		var rule = createRule(owner, """
			{"name":"Prep interviews","goalTemplate":"Draft interview questions for {{candidates}} for {{job}}.",
			 "trigger":{"type":"REPORT_STATUS_CHANGED","toStatuses":["INTERVIEWING"],"jobPostId":"%s"}}""".formatted(a.jobId()));
		assertThat(rule.path("trigger").path("toStatuses")).hasSize(1);

		setStatus(a.reportIds().get(0), "SHORTLISTED");
		tick();
		assertThat(ruleRuns()).as("not a watched status").isEmpty();

		setStatus(a.reportIds().get(0), "INTERVIEWING");
		tick();
		var runs = ruleRuns();
		assertThat(runs).hasSize(1);
		assertThat(runs.getFirst().getMentions()).extracting(AgentRun.Mention::getId).containsExactlyInAnyOrder(a.cvIds().getFirst(), a.jobId());
		assertThat(runs.getFirst().getHistory().getFirst().getText()).contains("from SHORTLISTED to INTERVIEWING");

		// The same change is never seen twice.
		awaitRuleRunsIdle();
		mongo.updateMulti(new Query(), new Update().set("status", AgentRun.STATUS_COMPLETED), AgentRun.class);
		tick();
		assertThat(ruleRuns()).hasSize(1);

		// A Copilot run moving a candidate never fires a status rule (loop guard).
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(a.reportIds().get(1)))),
			new Update().set("status", "INTERVIEWING").set("statusChangedAt", new java.util.Date())
				.set("statusChangedBy", "copilot:" + new ObjectId().toHexString()), "matching_reports");
		tick();
		assertThat(ruleRuns()).hasSize(1);

		var invalid = mvc.perform(post("/agent/rules").header("Authorization", owner).contentType(JSON).content("""
			{"name":"Bad","goalTemplate":"x","trigger":{"type":"REPORT_STATUS_CHANGED","toStatuses":["MAYBE"]}}"""))
			.andReturn().getResponse();
		assertThat(invalid.getStatus()).isEqualTo(400);
	}
}
