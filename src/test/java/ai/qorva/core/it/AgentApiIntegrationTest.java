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
import ai.qorva.core.dto.CandidateOutreachData;
import ai.qorva.core.service.mailbox.MailboxConnectionService;
import ai.qorva.core.service.mailbox.MailboxSender;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
	/** The only fake beyond the model: nothing leaves the test through a real mailbox. */
	@MockitoBean
	private MailboxConnectionService mailboxService;

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
		when(mailboxService.composerState(anyString(), anyString()))
			.thenReturn(new MailboxConnectionService.ComposerState(CandidateOutreachData.MailboxState.NONE, null));
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
	void anEmptyGoalAndARunOverEveryCopilotLimitAreRefused() throws Exception {
		assertThat(mvc.perform(post("/agent/runs").header("Authorization", owner).contentType(JSON).content("{\"goal\":\"  \"}"))
			.andReturn().getResponse().getStatus()).isEqualTo(400);

		// Tasks spent: the run may still be a candidate question or a library analysis, so it starts.
		mongo.updateMulti(Query.query(Criteria.where("tenantId").is(new ObjectId(a.tenantId()))),
			new Update().set("features.agentRuns.consumed", 100_000), "usage_monitoring");
		var accepted = mvc.perform(post("/agent/runs").header("Authorization", owner).contentType(JSON)
			.content("{\"goal\":\"How many Java developers?\"}")).andReturn().getResponse();
		assertThat(accepted.getStatus()).isEqualTo(202);
		awaitFinished(owner, json(accepted.getContentAsString()).path("id").asText());

		mongo.updateMulti(Query.query(Criteria.where("tenantId").is(new ObjectId(a.tenantId()))),
			new Update().set("features.aiResumeChats.consumed", 100_000).set("features.talentIntelligenceQueries.consumed", 100_000),
			"usage_monitoring");
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
		// The fixture's three runs of the owner's conversation, and the viewer's.
		assertThat(team.path("total").asLong()).isEqualTo(4);
		assertThat(team.path("items").findValuesAsText("userEmail")).contains(a.ownerEmail(), a.viewerEmail());
		var seenByOwner = json(mvc.perform(get("/agent/runs/" + viewersRun.path("id").asText()).header("Authorization", owner))
			.andReturn().getResponse().getContentAsString());
		assertThat(seenByOwner.path("canApprove").asBoolean()).isFalse();
		assertThat(json(mvc.perform(get("/agent/availability").header("Authorization", owner)).andReturn().getResponse().getContentAsString())
			.path("canViewTeam").asBoolean()).isTrue();
	}

	private long consumed(String feature) {
		var period = mongo.findOne(Query.query(Criteria.where("tenantId").is(new ObjectId(a.tenantId()))), Document.class, "usage_monitoring");
		var metrics = period.get("features", Document.class).get(feature, Document.class);
		return metrics.get("consumed") == null ? 0 : ((Number) metrics.get("consumed")).longValue();
	}

	@Test
	void aFocusedQuestionIsAnsweredByTheCandidateEngineAsIsAndCountsACandidateQuestion() throws Exception {
		// Copilot picks ask_about_candidate; the engine's own model call (its rules prompt) answers.
		when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
			var messages = inv.<Prompt>getArgument(0).getInstructions();
			if (messages.stream().anyMatch(m -> m.getText() != null && m.getText().contains("helping a recruiter evaluate one candidate"))) {
				return new ChatResponse(List.of(new Generation(new AssistantMessage("The screening report scores this match at 64%."))));
			}
			return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(),
				List.of(new AssistantMessage.ToolCall("call_1", "function", "ask_about_candidate", "{}"))))));
		});
		long chats = consumed("aiResumeChats");
		long tasks = consumed("agentRuns");

		var queued = start(owner, "{\"goal\":\"Is this candidate a fit?\",\"focus\":{\"cvId\":\"" + a.cvId()
			+ "\",\"jobPostId\":\"" + a.jobId() + "\"}}");
		assertThat(queued.path("focus").path("cvId").asText()).isEqualTo(a.cvId());
		var run = awaitFinished(owner, queued.path("id").asText());

		assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(run.path("finalAnswer").asText()).isEqualTo("The screening report scores this match at 64%.");
		assertThat(run.path("steps").get(0).path("tool").asText()).isEqualTo("ask_about_candidate");
		assertThat(consumed("aiResumeChats")).isEqualTo(chats + 1);
		assertThat(consumed("agentRuns")).isEqualTo(tasks);

		// A follow-up keeps the conversation's focus.
		var followUp = start(owner, "{\"goal\":\"What should I ask her?\",\"conversationId\":\""
			+ queued.path("conversationId").asText() + "\"}");
		assertThat(followUp.path("focus").path("jobPostId").asText()).isEqualTo(a.jobId());
		awaitFinished(owner, followUp.path("id").asText());
	}

	@Test
	void aFocusOnAnUnknownCandidateIsRefused() throws Exception {
		var response = mvc.perform(post("/agent/runs").header("Authorization", owner).contentType(JSON)
			.content("{\"goal\":\"Is she a fit?\",\"focus\":{\"cvId\":\"" + b.cvId() + "\",\"jobPostId\":\"" + a.jobId() + "\"}}"))
			.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("error.agent.focus_invalid");
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

	@Test
	void writeToolsChangeOnlyTheCallersRecordsAsTheUser() throws Exception {
		when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
			var messages = inv.<Prompt>getArgument(0).getInstructions();
			if (messages.getLast().getMessageType() == MessageType.TOOL) {
				return new ChatResponse(List.of(new Generation(new AssistantMessage("Tagged and noted."))));
			}
			return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(
				new AssistantMessage.ToolCall("call_1", "function", "add_cv_tags",
					"{\"cvIds\":[\"%s\",\"%s\"],\"tags\":[\"agent-shortlist\"]}".formatted(a.cvId(), b.cvId())),
				new AssistantMessage.ToolCall("call_2", "function", "add_note",
					"{\"cvId\":\"%s\",\"text\":\"Shortlisted by Copilot.\"}".formatted(a.cvId())))))));
		});

		var run = awaitFinished(owner, start(owner, "{\"goal\":\"Tag the best candidate and note why\"}").path("id").asText());

		assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
		assertThat(run.path("steps").findValuesAsText("summaryKey")).containsExactly("agent.step.add_cv_tags", "agent.step.add_note");
		assertThat(run.path("steps").get(0).path("summaryParams").path("count").asText()).isEqualTo("1");

		var mine = mongo.findById(new ObjectId(a.cvId()), Document.class, "cvs");
		assertThat(mine.getList("tags", String.class)).contains("agent-shortlist");
		assertThat(mine.getString("lastUpdatedBy")).isEqualTo(a.ownerEmail());
		var theirs = mongo.findById(new ObjectId(b.cvId()), Document.class, "cvs");
		assertThat(theirs.getList("tags", String.class, List.of())).doesNotContain("agent-shortlist");

		var note = mongo.findOne(Query.query(Criteria.where("targetId").is(a.cvId()).and("text").is("Shortlisted by Copilot.")),
			Document.class, "notes");
		assertThat(note).isNotNull();
		assertThat(note.getString("authorEmail")).isEqualTo(a.ownerEmail());
	}

	@Test
	void anEmailWaitsForApprovalThenGoesToTheProfileAddressWithTheRecruitersEdits() throws Exception {
		when(mailboxService.composerState(anyString(), anyString()))
			.thenReturn(new MailboxConnectionService.ComposerState(CandidateOutreachData.MailboxState.MICROSOFT, a.ownerEmail()));
		when(mailboxService.send(anyString(), anyString(), anyString(), anyString(), anyString()))
			.thenReturn(new MailboxSender.SendResult("msg-1", "thread-1", "https://outlook.test/msg-1"));
		when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
			var messages = inv.<Prompt>getArgument(0).getInstructions();
			if (messages.getLast().getMessageType() == MessageType.TOOL) {
				return new ChatResponse(List.of(new Generation(new AssistantMessage("Sent the intro."))));
			}
			return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(
				new AssistantMessage.ToolCall("call_1", "function", "send_outreach_email",
					"{\"cvId\":\"%s\",\"subject\":\"Intro\",\"body\":\"Hello\",\"to\":\"attacker@evil.test\"}".formatted(a.cvId())))))));
		});
		var cv = mongo.findById(new ObjectId(a.cvId()), Document.class, "cvs");
		var candidateEmail = cv.get("personalInformation", Document.class).get("contact", Document.class).getString("email");

		var paused = awaitFinished(owner, start(owner, "{\"goal\":\"Email the top candidate an intro\"}").path("id").asText());

		assertThat(paused.path("status").asText()).isEqualTo("AWAITING_APPROVAL");
		assertThat(paused.path("canApprove").asBoolean()).isTrue();
		var action = paused.path("pendingActions").get(0);
		assertThat(action.path("preview").path("to").asText()).isEqualTo(candidateEmail);
		// The card and the whole run view carry the profile's address only; the model's "to" is ignored.
		assertThat(paused.toString()).doesNotContain("attacker@evil.test");
		verify(mailboxService, never()).send(anyString(), anyString(), anyString(), anyString(), anyString());
		var runId = paused.path("id").asText();
		var decide = "/agent/runs/" + runId + "/actions/" + action.path("actionId").asText();

		// A decision on arguments the recruiter did not see is refused.
		var stale = mvc.perform(post(decide + "/approve").header("Authorization", owner).contentType(JSON)
			.content("{\"argsHash\":\"not-the-one\"}")).andReturn().getResponse();
		assertThat(stale.getStatus()).isEqualTo(409);

		mvc.perform(post(decide + "/approve").header("Authorization", owner).contentType(JSON)
			.content("{\"argsHash\":\"%s\",\"body\":\"Hello, edited\"}".formatted(action.path("argsHash").asText())));
		var done = awaitFinished(owner, runId);

		assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
		verify(mailboxService, times(1)).send(anyString(), eq(a.ownerEmail()), eq(candidateEmail), eq("Intro"), eq("Hello, edited"));
		var sent = mongo.findOne(Query.query(Criteria.where("cvId").is(a.cvId()).and("agentRunId").is(runId)), Document.class, "candidate_outreach");
		assertThat(sent).isNotNull();
		assertThat(sent.getString("status")).isEqualTo("SENT");

		// Deciding again on the same card is refused: it is already done.
		var again = mvc.perform(post(decide + "/reject").header("Authorization", owner).contentType(JSON)
			.content("{\"argsHash\":\"%s\"}".formatted(action.path("argsHash").asText()))).andReturn().getResponse();
		assertThat(again.getStatus()).isEqualTo(409);
	}

	@Test
	void onlyTheRunsOwnUserMayDecide() throws Exception {
		var run = mongo.findById(new ObjectId(a.agentRunId()), AgentRun.class);
		var action = new AgentRun.PendingAction();
		action.setActionId("act-1");
		action.setArgsHash("h");
		action.setStatus(AgentRun.PendingAction.PENDING);
		action.setTool("send_outreach_email");
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(run.getId()))),
			new Update().set("status", AgentRun.STATUS_AWAITING_APPROVAL).set("pendingActions", List.of(action)), AgentRun.class);
		grantUseAgentToViewer();
		var viewer = fixture.bearer(a.viewerEmail(), a.tenantId());

		var response = mvc.perform(post("/agent/runs/" + run.getId() + "/actions/act-1/approve").header("Authorization", viewer)
			.contentType(JSON).content("{\"argsHash\":\"h\"}")).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(mongo.findById(new ObjectId(run.getId()), AgentRun.class).getPendingActions().getFirst().getStatus())
			.isEqualTo(AgentRun.PendingAction.PENDING);
	}

	@Test
	void aFollowUpStillKnowsTheRecordsMentionedEarlierInTheConversation() throws Exception {
		var first = start(owner, "{\"goal\":\"It is for this job\",\"mentions\":[{\"type\":\"JOB\",\"id\":\"%s\"}]}".formatted(a.jobId()));
		awaitFinished(owner, first.path("id").asText());

		var second = start(owner, "{\"goal\":\"Check all candidates above 60%%\",\"conversationId\":\"%s\"}"
			.formatted(first.path("conversationId").asText()));
		awaitFinished(owner, second.path("id").asText());

		var replayed = mongo.findById(new ObjectId(second.path("id").asText()), AgentRun.class).getHistory().getFirst();
		assertThat(replayed.getRole()).isEqualTo("user");
		assertThat(replayed.getText()).startsWith("It is for this job").contains("jobId=" + a.jobId());
	}
}
