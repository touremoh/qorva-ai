package ai.qorva.core.service.agent;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.service.UsageMonitoringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentRunnerApprovalTest {

	private static final String TENANT = "64b7f0f0f0f0f0f0f0f0f0f0";
	private static final AgentToolContext CTX = new AgentToolContext(TENANT, "owner@a.test", "en", null, "run-1");

	@Mock private AgentRunStore store;
	@Mock private AgentModelClient modelClient;
	@Mock private AgentToolRegistry registry;
	@Mock private AgentExecutionScope scope;
	@Mock private UsageMonitoringService usageMonitoringService;

	private final AgentProperties properties = new AgentProperties();
	private AgentRunner runner;

	/** An approval tool whose preview and execution are observable. */
	static final class FakeSend implements AgentTool {
		AgentToolResult previewResult = AgentToolResult.ok(Map.of("to", "ana@x.test", "subject", "Hi"), "agent.step.send_outreach_email",
			Map.of("name", "Ana"), List.of());
		final List<AgentApproval> executed = new ArrayList<>();
		int previews;

		public String name() { return "send_outreach_email"; }
		public String description() { return ""; }
		public String inputSchema() { return "{}"; }
		public AgentRiskTier tier() { return AgentRiskTier.APPROVAL; }
		public Set<UserActionsEnum> requiredActions() { return Set.of(); }
		public boolean outbound() { return true; }
		public AgentToolResult preview(JsonNode args, AgentToolContext ctx) { previews++; return previewResult; }
		public AgentToolResult execute(JsonNode args, AgentToolContext ctx) { throw new AssertionError("never without approval"); }
		public AgentToolResult execute(JsonNode args, AgentToolContext ctx, AgentApproval approval) {
			executed.add(approval);
			return AgentToolResult.ok(Map.of("sent", true), "agent.step.email_sent", Map.of("name", "Ana"), List.of());
		}
	}

	static final class FakeRead implements AgentTool {
		int calls;
		public String name() { return "get_cv"; }
		public String description() { return ""; }
		public String inputSchema() { return "{}"; }
		public AgentRiskTier tier() { return AgentRiskTier.READ; }
		public Set<UserActionsEnum> requiredActions() { return Set.of(); }
		public AgentToolResult execute(JsonNode args, AgentToolContext ctx) { calls++; return AgentToolResult.ok(Map.of("name", "Ana"), "k", Map.of(), List.of()); }
	}

	private final FakeSend send = new FakeSend();
	private final FakeRead read = new FakeRead();

	@BeforeEach
	void setUp() throws Exception {
		runner = new AgentRunner(store, modelClient, registry, scope, properties, usageMonitoringService, new ObjectMapper());
		when(scope.call(any(), any())).thenAnswer(inv -> inv.<AgentExecutionScope.AgentWork<?>>getArgument(1).call(CTX));
		when(store.saveProgress(any())).thenReturn(true);
		when(store.pause(any())).thenReturn(true);
		when(store.finish(any())).thenReturn(true);
		when(registry.allowedFor(any())).thenReturn(List.of(send, read));
		when(registry.allowed(eq("send_outreach_email"), any())).thenReturn(Optional.of(send));
		when(registry.allowed(eq("get_cv"), any())).thenReturn(Optional.of(read));
	}

	private static AgentRun run() {
		var run = new AgentRun();
		run.setId("run-1");
		run.setTenantId(TENANT);
		run.setUserEmail("owner@a.test");
		run.setStatus(AgentRun.STATUS_RUNNING);
		run.setHistory(new ArrayList<>(List.of(AgentHistory.user("Email Ana an intro"))));
		return run;
	}

	private static ChatResponse calls(AssistantMessage.ToolCall... calls) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(calls)))));
	}

	private static AssistantMessage.ToolCall call(String id, String tool) {
		return new AssistantMessage.ToolCall(id, "function", tool, "{\"cvId\":\"cv-1\",\"subject\":\"Hi\",\"body\":\"Hello\"}");
	}

	private static ChatResponse text(String answer) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
	}

	@Test
	void anApprovalCallPausesTheRunWithACardAndNothingIsSent() {
		when(modelClient.call(anyList(), anyList())).thenReturn(calls(call("c1", "send_outreach_email")));
		var run = run();

		runner.run(run);

		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_AWAITING_APPROVAL);
		assertThat(run.getApprovalExpiresAt()).isNotNull();
		assertThat(run.getPendingActions()).singleElement().satisfies(a -> {
			assertThat(a.getStatus()).isEqualTo(AgentRun.PendingAction.PENDING);
			assertThat(a.getToolCallId()).isEqualTo("c1");
			assertThat(a.getArgsHash()).isEqualTo(AgentRunner.argsHash("send_outreach_email", a.getArgsJson()));
			assertThat(a.getPreview()).containsEntry("to", "ana@x.test");
		});
		assertThat(run.getSteps()).singleElement().satisfies(s -> {
			assertThat(s.getState()).isEqualTo(AgentRun.Step.STATE_PENDING);
			assertThat(s.getTier()).isEqualTo("APPROVAL");
		});
		assertThat(send.executed).isEmpty();
		verify(store).pause(run);
		verify(store, never()).finish(any());
	}

	@Test
	void aRuleRunWaitsLongerForApprovalAndIsToldNobodyIsWatching() {
		when(modelClient.call(anyList(), anyList())).thenReturn(calls(call("c1", "send_outreach_email")));
		var chat = run();
		runner.run(chat);
		var rule = run();
		rule.setOrigin(AgentRun.ORIGIN_RULE);
		rule.setRuleName("Invite strong matches");
		runner.run(rule);

		assertThat(java.time.Duration.between(chat.getApprovalExpiresAt(), rule.getApprovalExpiresAt()).toHours())
			.isEqualTo(properties.getRules().getApprovalTtlHours() - properties.getApprovalTtlHours());
		assertThat(runner.systemPrompt(rule)).contains("standing rule \"Invite strong matches\"").contains("72 hours");
		assertThat(runner.systemPrompt(chat)).doesNotContain("standing rule");
	}

	@Test
	void aMixedTurnRunsTheReadAndPausesOnTheSend() {
		when(modelClient.call(anyList(), anyList())).thenReturn(calls(call("c1", "get_cv"), call("c2", "send_outreach_email")));
		var run = run();

		runner.run(run);

		assertThat(read.calls).isEqualTo(1);
		assertThat(run.getPendingToolResults()).extracting(AgentRun.ToolResult::getId).containsExactly("c1");
		assertThat(run.getPendingActions()).extracting(AgentRun.PendingAction::getToolCallId).containsExactly("c2");
		assertThat(run.getHistory()).extracting(AgentRun.HistoryMessage::getRole).containsExactly("user", "assistant");
	}

	@Test
	void resumingSendsTheApprovedEmailWithTheEditsAndReportsTheRejectedOne() {
		when(modelClient.call(anyList(), anyList()))
			.thenReturn(calls(call("c1", "send_outreach_email"), call("c2", "send_outreach_email")))
			.thenReturn(text("Sent one, the other was declined."));
		var run = run();
		runner.run(run);
		var actions = run.getPendingActions();
		actions.get(0).setStatus(AgentRun.PendingAction.APPROVED);
		actions.get(0).setEditedBody("Hello, edited");
		actions.get(1).setStatus(AgentRun.PendingAction.REJECTED);
		actions.get(1).setReason("Not this one");
		run.setStatus(AgentRun.STATUS_RUNNING);

		runner.run(run);

		assertThat(send.executed).singleElement().satisfies(a -> {
			assertThat(a.body()).isEqualTo("Hello, edited");
			assertThat(a.subject()).isNull();
		});
		assertThat(send.previews).as("checked again before sending").isEqualTo(3);
		assertThat(run.getOutboundCount()).isEqualTo(1);
		assertThat(run.getSteps()).extracting(AgentRun.Step::getState)
			.containsExactly(AgentRun.Step.STATE_OK, AgentRun.Step.STATE_REJECTED);
		var toolMessage = run.getHistory().get(2);
		assertThat(toolMessage.getRole()).isEqualTo("tool");
		assertThat(toolMessage.getToolResults()).extracting(AgentRun.ToolResult::getId).containsExactly("c1", "c2");
		assertThat(toolMessage.getToolResults().get(1).getData()).contains("declined").contains("Not this one");
		assertThat(run.getPendingActions()).isEmpty();
		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_COMPLETED);
	}

	@Test
	void anActionInterruptedWhileExecutingIsNeverSentAgain() {
		when(modelClient.call(anyList(), anyList())).thenReturn(calls(call("c1", "send_outreach_email"))).thenReturn(text("ok"));
		var run = run();
		runner.run(run);
		run.getPendingActions().getFirst().setStatus(AgentRun.PendingAction.EXECUTING);
		run.getSteps().getFirst().setState(AgentRun.Step.STATE_EXECUTING);

		runner.run(run);

		assertThat(send.executed).isEmpty();
		assertThat(run.getSteps().getFirst().getState()).isEqualTo(AgentRun.Step.STATE_ERROR);
		assertThat(run.getHistory().get(2).getToolResults().getFirst().getData()).contains("outcome unknown");
	}

	@Test
	void theOutboundCapRefusesFurtherSendsInTheSameRun() {
		properties.setMaxOutboundPerRun(1);
		when(modelClient.call(anyList(), anyList())).thenReturn(calls(call("c1", "send_outreach_email"), call("c2", "send_outreach_email")));
		var run = run();

		runner.run(run);

		assertThat(run.getPendingActions()).hasSize(1);
		assertThat(run.getPendingToolResults()).singleElement().satisfies(r -> assertThat(r.getData()).contains("limit"));
	}

	@Test
	void aRefusedPreviewGoesBackToTheModelInsteadOfACard() {
		send.previewResult = AgentToolResult.error("This candidate asked not to be contacted.");
		when(modelClient.call(anyList(), anyList())).thenReturn(calls(call("c1", "send_outreach_email"))).thenReturn(text("Could not."));
		var run = run();

		runner.run(run);

		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_COMPLETED);
		assertThat(run.getPendingActions()).isEmpty();
		assertThat(run.getSteps().getFirst().getState()).isEqualTo(AgentRun.Step.STATE_ERROR);
		verify(store, never()).pause(any());
	}

	@Test
	void theArgumentsHashChangesWithTheArguments() {
		assertThat(AgentRunner.argsHash("send_outreach_email", "{\"a\":1}"))
			.isNotEqualTo(AgentRunner.argsHash("send_outreach_email", "{\"a\":2}"))
			.isNotEqualTo(AgentRunner.argsHash("start_screening", "{\"a\":1}"));
	}
}
