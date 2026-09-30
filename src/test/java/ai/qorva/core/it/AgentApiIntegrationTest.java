package ai.qorva.core.it;

import ai.qorva.core.dao.entity.AgentRun;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Copilot end to end with the agent switched on and the model scripted: a run is queued by the API,
 * executed by the worker with a real tool against Mongo, metered, and visible according to who asks.
 */
@TestPropertySource(properties = {"qorva.ai.agent.enabled=true", "qorva.ai.agent.poll-delay-ms=200"})
class AgentApiIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@MockitoBean
	private ChatModel chatModel;

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private MongoTemplate mongo;
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
		// Scripted model: first turn searches, once it has a tool result it answers.
		when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
			var messages = inv.<Prompt>getArgument(0).getInstructions();
			var last = messages.getLast();
			if (last.getMessageType() == MessageType.TOOL) {
				return new ChatResponse(List.of(new Generation(new AssistantMessage("You have Java candidates."))));
			}
			return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall("call_1", "function", "search_cvs", "{\"pageSize\":5}"))))));
		});
	}

	private JsonNode json(String body) throws Exception {
		return objectMapper.readTree(body);
	}

	private JsonNode start(String token, String body) throws Exception {
		var response = mvc.perform(post("/agent/runs").header("Authorization", token).contentType(JSON).content(body))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(202);
		return json(response.getContentAsString());
	}

	private JsonNode awaitFinished(String token, String runId) throws Exception {
		var deadline = System.currentTimeMillis() + 20_000;
		while (System.currentTimeMillis() < deadline) {
			var run = json(mvc.perform(get("/agent/runs/" + runId).header("Authorization", token)).andReturn().getResponse().getContentAsString());
			if (!List.of("QUEUED", "RUNNING").contains(run.path("status").asText())) return run;
			Thread.sleep(100);
		}
		throw new AssertionError("run " + runId + " did not finish");
	}

	private void grantUseAgentToViewer() {
		mongo.updateFirst(Query.query(Criteria.where("email").is(a.viewerEmail())),
			new Update().push("authorities", new Document("role", "ACCOUNT_MANAGER").append("action", "USE_AGENT").append("permission", "ALLOWED")),
			"users");
	}

	@Test
	void aRunIsExecutedWithRealToolsMeteredAndNeverExposesItsHistory() throws Exception {
		var queued = start(owner, "{\"goal\":\"How many Java developers do we have?\"}");
		assertThat(queued.path("status").asText()).isEqualTo("QUEUED");

		var run = awaitFinished(owner, queued.path("id").asText());

		assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(run.path("finalAnswer").asText()).isEqualTo("You have Java candidates.");
		assertThat(run.path("steps")).hasSize(1);
		var step = run.path("steps").get(0);
		assertThat(step.path("tool").asText()).isEqualTo("search_cvs");
		assertThat(step.path("state").asText()).isEqualTo("OK");
		assertThat(step.path("summaryKey").asText()).isEqualTo("agent.step.search_cvs");
		assertThat(step.path("links").size()).isPositive();
		assertThat(run.has("history")).isFalse();
		assertThat(run.toString()).doesNotContain("\"tokens\"");

		var usage = json(mvc.perform(get("/usage-monitoring/current").header("Authorization", owner)).andReturn().getResponse().getContentAsString());
		assertThat(usage.path("features").path("agentRuns").path("consumed").asInt()).isEqualTo(1);

		var stored = mongo.findById(new ObjectId(run.path("id").asText()), AgentRun.class);
		assertThat(stored.getCreatedBy()).isEqualTo(a.ownerEmail());
		assertThat(stored.getSteps().getFirst().getLinks()).allSatisfy(l -> assertThat(a.cvIds()).contains(l.getId()));
	}

	@Test
	void mentionsAreResolvedInTheCallersTenantOnly() throws Exception {
		var run = start(owner, """
			{"goal":"Compare these","mentions":[{"type":"CV","id":"%s","name":"forged"},{"type":"CV","id":"%s"},{"type":"JOB","id":"%s"}]}
			""".formatted(a.cvId(), b.cvId(), a.jobId()));

		assertThat(run.path("mentions")).hasSize(2);
		assertThat(run.path("mentions").findValuesAsText("id")).containsExactly(a.cvId(), a.jobId());
		assertThat(run.path("mentions").get(0).path("name").asText()).isNotEqualTo("forged");
		awaitFinished(owner, run.path("id").asText());
	}

	@Test
	void aSecondRunWhileOneIsActiveIsRefused() throws Exception {
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(a.agentRunId()))),
			Update.update("status", AgentRun.STATUS_AWAITING_APPROVAL), AgentRun.class);

		var response = mvc.perform(post("/agent/runs").header("Authorization", owner).contentType(JSON)
			.content("{\"goal\":\"Another task\"}")).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat(response.getContentAsString()).contains("error.agent.run_active");
	}

	@Test
	void anEmptyGoalAndARunOverTheLimitAreRefused() throws Exception {
		assertThat(mvc.perform(post("/agent/runs").header("Authorization", owner).contentType(JSON).content("{\"goal\":\"  \"}"))
			.andReturn().getResponse().getStatus()).isEqualTo(400);

		mongo.updateMulti(Query.query(Criteria.where("tenantId").is(new ObjectId(a.tenantId()))),
			new Update().set("features.agentRuns.consumed", 500), "usage_monitoring");
		var response = mvc.perform(post("/agent/runs").header("Authorization", owner).contentType(JSON)
			.content("{\"goal\":\"Anything\"}")).andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("error.usage.agent_limit_exceeded");
	}

	@Test
	void usersWithoutUseAgentAreRefused() throws Exception {
		var viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
		assertThat(mvc.perform(get("/agent/availability").header("Authorization", viewer)).andReturn().getResponse().getStatus())
			.isEqualTo(403);
	}

	@Test
	void theTeamSeesEveryonesRunsOnlyWithManageUsers() throws Exception {
		grantUseAgentToViewer();
		var viewer = fixture.bearer(a.viewerEmail(), a.tenantId());

		// The viewer can use Copilot but can't see the team, nor the owner's run.
		assertThat(mvc.perform(get("/agent/runs").param("scope", "team").header("Authorization", viewer))
			.andReturn().getResponse().getStatus()).isEqualTo(403);
		assertThat(mvc.perform(get("/agent/runs/" + a.agentRunId()).header("Authorization", viewer))
			.andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(json(mvc.perform(get("/agent/runs").header("Authorization", viewer)).andReturn().getResponse().getContentAsString())
			.path("total").asLong()).isZero();

		var viewersRun = start(viewer, "{\"goal\":\"How many Java developers?\"}");
		awaitFinished(viewer, viewersRun.path("id").asText());

		// The owner manages users: sees both runs, may read the viewer's but never approve for them.
		var team = json(mvc.perform(get("/agent/runs").param("scope", "team").header("Authorization", owner))
			.andReturn().getResponse().getContentAsString());
		assertThat(team.path("total").asLong()).isEqualTo(2);
		assertThat(team.path("items").findValuesAsText("userEmail")).contains(a.ownerEmail(), a.viewerEmail());
		var seenByOwner = json(mvc.perform(get("/agent/runs/" + viewersRun.path("id").asText()).header("Authorization", owner))
			.andReturn().getResponse().getContentAsString());
		assertThat(seenByOwner.path("canApprove").asBoolean()).isFalse();
		assertThat(json(mvc.perform(get("/agent/availability").header("Authorization", owner)).andReturn().getResponse().getContentAsString())
			.path("canViewTeam").asBoolean()).isTrue();
	}

	@Test
	void aConversationContinuesAndCanOnlyBeDeletedWhenIdle() throws Exception {
		var first = start(owner, "{\"goal\":\"Who knows Kotlin?\"}");
		var conversationId = first.path("conversationId").asText();
		awaitFinished(owner, first.path("id").asText());

		var second = start(owner, "{\"goal\":\"And Go?\",\"conversationId\":\"" + conversationId + "\"}");
		assertThat(second.path("conversationId").asText()).isEqualTo(conversationId);
		assertThat(second.path("title").asText()).isEqualTo("Who knows Kotlin?");
		awaitFinished(owner, second.path("id").asText());
		var stored = mongo.findById(new ObjectId(second.path("id").asText()), AgentRun.class);
		assertThat(stored.getHistory().getFirst().getText()).isEqualTo("Who knows Kotlin?");

		var turns = json(mvc.perform(get("/agent/conversations/" + conversationId).header("Authorization", owner))
			.andReturn().getResponse().getContentAsString());
		assertThat(turns).hasSize(2);

		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(second.path("id").asText()))),
			Update.update("status", AgentRun.STATUS_AWAITING_APPROVAL), AgentRun.class);
		assertThat(mvc.perform(delete("/agent/conversations/" + conversationId).header("Authorization", owner))
			.andReturn().getResponse().getStatus()).isEqualTo(409);

		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(second.path("id").asText()))),
			new Update().set("status", AgentRun.STATUS_COMPLETED).set("finishedAt", Instant.now()), AgentRun.class);
		assertThat(mvc.perform(delete("/agent/conversations/" + conversationId).header("Authorization", owner))
			.andReturn().getResponse().getStatus()).isEqualTo(204);
		assertThat(mongo.count(Query.query(Criteria.where("conversationId").is(conversationId)), AgentRun.class)).isZero();
	}
}
