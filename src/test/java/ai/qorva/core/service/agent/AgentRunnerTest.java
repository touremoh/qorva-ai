package ai.qorva.core.service.agent;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.service.UsageMonitoringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentRunnerTest {

	private static final String TENANT = "64b7f0f0f0f0f0f0f0f0f0f0";
	private static final AgentToolContext CTX = new AgentToolContext(TENANT, "owner@a.test", "en", null);

	@Mock private AgentRunStore store;
	@Mock private AgentModelClient modelClient;
	@Mock private AgentToolRegistry registry;
	@Mock private AgentExecutionScope scope;
	@Mock private UsageMonitoringService usageMonitoringService;

	private final AgentProperties properties = new AgentProperties();
	private AgentRunner runner;

	@BeforeEach
	void setUp() throws Exception {
		runner = new AgentRunner(store, modelClient, registry, scope, properties, usageMonitoringService, new ObjectMapper());
		when(scope.call(any(), any())).thenAnswer(inv -> inv.<AgentExecutionScope.AgentWork<?>>getArgument(1).call(CTX));
		when(store.saveProgress(any())).thenReturn(true);
		when(store.finish(any())).thenReturn(true);
	}

	private static AgentRun run() {
		var run = new AgentRun();
		run.setId("64b7f0f0f0f0f0f0f0f0f0f1");
		run.setTenantId(TENANT);
		run.setUserEmail("owner@a.test");
		run.setLanguage("en");
		run.setStatus(AgentRun.STATUS_RUNNING);
		run.setHistory(new ArrayList<>(List.of(AgentHistory.user("How many Java developers?"))));
		return run;
	}

	private static ChatResponse text(String answer) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
	}

	private static ChatResponse toolCall(String id, String tool, String args) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(),
			List.of(new AssistantMessage.ToolCall(id, "function", tool, args))))));
	}

	private static AgentTool tool(String name, AgentRiskTier tier, AgentToolResult result) {
		return new AgentTool() {
			public String name() { return name; }
			public String description() { return name; }
			public String inputSchema() { return "{\"type\":\"object\"}"; }
			public AgentRiskTier tier() { return tier; }
			public Set<UserActionsEnum> requiredActions() { return Set.of(); }
			public AgentToolResult execute(JsonNode args, AgentToolContext ctx) { return result; }
		};
	}

	private void offer(AgentTool tool) {
		when(registry.allowedFor(any())).thenReturn(List.of(tool));
		when(registry.allowed(eq(tool.name()), any())).thenReturn(Optional.of(tool));
	}

	@Test
	void answersWithoutToolsInOneStepAndMetersTheRunOnce() {
		when(modelClient.call(anyList(), anyList())).thenReturn(text("There are 7."));
		var run = run();

		runner.run(run);

		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_COMPLETED);
		assertThat(run.getFinalAnswer()).isEqualTo("There are 7.");
		assertThat(run.getStepCount()).isEqualTo(1);
		verify(usageMonitoringService, times(1)).incrementUsage(TENANT, UsageMonitoringService.FeatureKey.AGENT_RUNS, 1);
		verify(store).finish(run);
	}

	@Test
	void executesToolCallsPersistingEachStepBeforeRunningIt() {
		var searched = new AtomicInteger();
		var search = new AgentTool() {
			public String name() { return "search_cvs"; }
			public String description() { return ""; }
			public String inputSchema() { return "{}"; }
			public AgentRiskTier tier() { return AgentRiskTier.READ; }
			public Set<UserActionsEnum> requiredActions() { return Set.of(); }
			public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
				searched.incrementAndGet();
				assertThat(args.get("skills").get(0).asText()).isEqualTo("Java");
				return AgentToolResult.ok(Map.of("total", 7), "agent.step.search_cvs", Map.of("count", "7"),
					List.of(new AgentRun.Link("CV", "cv-1", "Ana")));
			}
		};
		offer(search);
		when(modelClient.call(anyList(), anyList()))
			.thenReturn(toolCall("c1", "search_cvs", "{\"skills\":[\"Java\"]}"))
			.thenReturn(text("There are 7 Java developers."));
		var run = run();

		runner.run(run);

		assertThat(searched).hasValue(1);
		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_COMPLETED);
		assertThat(run.getSteps()).singleElement().satisfies(step -> {
			assertThat(step.getState()).isEqualTo(AgentRun.Step.STATE_OK);
			assertThat(step.getSummaryKey()).isEqualTo("agent.step.search_cvs");
			assertThat(step.getSummaryParams()).containsEntry("count", "7");
			assertThat(step.getLinks()).extracting(AgentRun.Link::getId).containsExactly("cv-1");
		});
		assertThat(run.getHistory()).extracting(AgentRun.HistoryMessage::getRole)
			.containsExactly("user", "assistant", "tool", "assistant");
		assertThat(run.getHistory().get(2).getToolResults()).singleElement()
			.satisfies(r -> assertThat(r.getData()).isEqualTo("{\"total\":7}"));
		assertThat(run.getToolCallCount()).isEqualTo(1);
		verify(usageMonitoringService, times(1)).incrementUsage(TENANT, UsageMonitoringService.FeatureKey.AGENT_RUNS, 1);
	}

	@Test
	void theStepIsSavedAsExecutingBeforeTheToolRuns() {
		InOrder order = inOrder(store);
		var states = new ArrayList<String>();
		when(store.saveProgress(any())).thenAnswer(inv -> {
			var saved = inv.<AgentRun>getArgument(0);
			if (!saved.getSteps().isEmpty()) states.add(saved.getSteps().getLast().getState());
			return true;
		});
		offer(tool("search_cvs", AgentRiskTier.READ, AgentToolResult.ok(Map.of(), "k", Map.of(), List.of())));
		when(modelClient.call(anyList(), anyList())).thenReturn(toolCall("c1", "search_cvs", "{}")).thenReturn(text("done"));

		runner.run(run());

		// Saved once before the tool runs (EXECUTING), once after the turn (OK), then finished.
		assertThat(states).containsExactly(AgentRun.Step.STATE_EXECUTING, AgentRun.Step.STATE_OK);
		order.verify(store, times(2)).saveProgress(any());
		order.verify(store).finish(any());
	}

	@Test
	void aBudgetEndsTheRunAsCompletedAndFlagsItStoppedEarly() {
		properties.setMaxSteps(2);
		offer(tool("search_cvs", AgentRiskTier.READ, AgentToolResult.ok(Map.of(), "k", Map.of(), List.of())));
		when(modelClient.call(anyList(), anyList())).thenReturn(toolCall("c1", "search_cvs", "{}"));
		var run = run();

		runner.run(run);

		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_COMPLETED);
		assertThat(run.getStoppedEarly()).isTrue();
		verify(modelClient, times(2)).call(anyList(), anyList());
	}

	@Test
	void aCancelRequestStopsTheRunBeforeTheNextModelCall() {
		offer(tool("search_cvs", AgentRiskTier.READ, AgentToolResult.ok(Map.of(), "k", Map.of(), List.of())));
		when(modelClient.call(anyList(), anyList())).thenReturn(toolCall("c1", "search_cvs", "{}"));
		when(store.isCancelRequested(any())).thenReturn(false).thenReturn(true);
		var run = run();

		runner.run(run);

		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_CANCELLED);
		verify(modelClient, times(1)).call(anyList(), anyList());
	}

	@Test
	void threeConsecutiveToolErrorsFailTheRun() {
		offer(tool("get_cv", AgentRiskTier.READ, AgentToolResult.error("CV not found")));
		when(modelClient.call(anyList(), anyList())).thenReturn(toolCall("c1", "get_cv", "{\"cvId\":\"x\"}"));
		var run = run();

		runner.run(run);

		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_FAILED);
		assertThat(run.getFailureReason()).isEqualTo(QorvaErrorCodes.AGENT_TOO_MANY_ERRORS);
		assertThat(run.getSteps()).hasSize(AgentRunner.MAX_CONSECUTIVE_ERRORS)
			.allSatisfy(s -> assertThat(s.getState()).isEqualTo(AgentRun.Step.STATE_ERROR));
	}

	/** An approval tool without a preview can never become a card, and so never runs. */
	@Test
	void anApprovalToolNeverRunsUnattended() {
		var executed = new AtomicInteger();
		var send = new AgentTool() {
			public String name() { return "send_outreach_email"; }
			public String description() { return ""; }
			public String inputSchema() { return "{}"; }
			public AgentRiskTier tier() { return AgentRiskTier.APPROVAL; }
			public Set<UserActionsEnum> requiredActions() { return Set.of(); }
			public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
				executed.incrementAndGet();
				return AgentToolResult.ok(Map.of(), "k", Map.of(), List.of());
			}
		};
		offer(send);
		when(modelClient.call(anyList(), anyList())).thenReturn(toolCall("c1", "send_outreach_email", "{}")).thenReturn(text("ok"));

		var run = run();
		runner.run(run);

		assertThat(executed).hasValue(0);
		assertThat(run.getSteps().getFirst().getState()).isEqualTo(AgentRun.Step.STATE_ERROR);
	}

	@Test
	void aToolTheUserMayNotUseIsRefused() {
		when(registry.allowedFor(any())).thenReturn(List.of());
		when(registry.allowed(eq("get_report"), any())).thenReturn(Optional.empty());
		when(modelClient.call(anyList(), anyList())).thenReturn(toolCall("c1", "get_report", "{}")).thenReturn(text("ok"));
		var run = run();

		runner.run(run);

		assertThat(run.getSteps().getFirst().getError()).contains("Tool not available");
		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_COMPLETED);
	}

	@Test
	void anInterruptedStepIsClosedNotReplayedAndTheModelIsTold() {
		var executed = new AtomicInteger();
		var search = mock(AgentTool.class);
		when(search.name()).thenReturn("search_cvs");
		when(registry.allowedFor(any())).thenReturn(List.of(search));
		when(modelClient.call(anyList(), anyList())).thenReturn(text("Let me summarise."));
		var run = run();
		run.getHistory().add(new AgentRun.HistoryMessage("assistant", "", List.of(new AgentRun.ToolCall("c1", "search_cvs", "{}")), null));
		var interrupted = new AgentRun.Step();
		interrupted.setSeq(1);
		interrupted.setTool("search_cvs");
		interrupted.setState(AgentRun.Step.STATE_EXECUTING);
		run.getSteps().add(interrupted);
		run.setMetered(true);

		runner.run(run);

		assertThat(executed).hasValue(0);
		assertThat(interrupted.getState()).isEqualTo(AgentRun.Step.STATE_ERROR);
		assertThat(interrupted.getSummaryKey()).isEqualTo("agent.step.interrupted");
		assertThat(run.getHistory()).extracting(AgentRun.HistoryMessage::getRole)
			.containsExactly("user", "assistant", "tool", "assistant");
		assertThat(run.getHistory().get(2).getToolResults().getFirst().getData()).contains("outcome unknown");
		verify(usageMonitoringService, never()).incrementUsage(any(), any(), anyInt());
		verify(registry, never()).allowed(eq("search_cvs"), any());
	}

	@Test
	void aModelFailureFailsTheRun() {
		when(modelClient.call(anyList(), anyList())).thenThrow(new RuntimeException("503 from provider"));
		var run = run();

		runner.run(run);

		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_FAILED);
		assertThat(run.getFailureReason()).isEqualTo(QorvaErrorCodes.AGENT_MODEL_FAILED);
		// A run the model never worked on is not billed.
		assertThat(run.isMetered()).isFalse();
		verify(usageMonitoringService, never()).incrementUsage(any(), any(), anyInt());
	}

	@Test
	void aRunWhoseUserCanNoLongerActFailsWithThatReason() throws Exception {
		doThrow(new AgentPreconditionException(QorvaErrorCodes.AUTH_SUBSCRIPTION_INACTIVE, "canceled")).when(scope).call(any(), any());
		var run = run();

		runner.run(run);

		assertThat(run.getStatus()).isEqualTo(AgentRun.STATUS_FAILED);
		assertThat(run.getFailureReason()).isEqualTo(QorvaErrorCodes.AUTH_SUBSCRIPTION_INACTIVE);
		verify(modelClient, never()).call(anyList(), anyList());
	}

	@Test
	void losingTheLeaseStopsWorkWithoutFinishing() {
		offer(tool("search_cvs", AgentRiskTier.READ, AgentToolResult.ok(Map.of(), "k", Map.of(), List.of())));
		when(modelClient.call(anyList(), anyList())).thenReturn(toolCall("c1", "search_cvs", "{}"));
		when(store.saveProgress(any())).thenReturn(false);

		runner.run(run());

		verify(store, never()).finish(any());
	}

	@Test
	void theSystemPromptNamesTheRunLanguage() {
		var run = run();
		run.setLanguage("fr-FR,fr;q=0.9");
		assertThat(runner.systemPrompt(run)).contains("Always answer in French.");
		assertThat(AgentRunner.languageName(null)).isEqualTo("English");
		assertThat(AgentRunner.languageName("xx")).isEqualTo("English");
	}
}
