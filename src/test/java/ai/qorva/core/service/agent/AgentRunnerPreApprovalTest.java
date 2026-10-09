package ai.qorva.core.service.agent;

import ai.qorva.core.dao.entity.AgentRun;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A rule's pre-approval covers matching and profile-update requests, each within its cap, and only in that rule's runs. */
class AgentRunnerPreApprovalTest {

	private static AgentRun ruleRun(Integer maxActions) {
		var run = new AgentRun();
		run.setOrigin(AgentRun.ORIGIN_RULE);
		run.setAutoApproveMaxActions(maxActions);
		return run;
	}

	private static AgentTool tool(String name) {
		var tool = mock(AgentTool.class);
		when(tool.name()).thenReturn(name);
		return tool;
	}

	private static AgentToolResult card(int estimatedActions) {
		return AgentToolResult.ok(Map.of("estimatedActions", estimatedActions), "agent.step.start_screening", Map.of(), List.of());
	}

	@Test
	void matchingWithinTheCapRunsWithoutAsking() {
		assertThat(AgentRunner.preApproved(ruleRun(30), tool("start_screening"), card(30))).isTrue();
	}

	@Test
	void aDearerMatchingStillWaitsForApproval() {
		assertThat(AgentRunner.preApproved(ruleRun(30), tool("start_screening"), card(31))).isFalse();
	}

	@Test
	void otherToolsAndUnapprovedRulesAndChatRunsAlwaysAsk() {
		assertThat(AgentRunner.preApproved(ruleRun(30), tool("send_outreach_email"), card(1))).isFalse();
		assertThat(AgentRunner.preApproved(ruleRun(null), tool("start_screening"), card(1))).isFalse();
		var chat = ruleRun(30);
		chat.setOrigin(AgentRun.ORIGIN_CHAT);
		assertThat(AgentRunner.preApproved(chat, tool("start_screening"), card(1))).isFalse();
		// A card without a cost is never pre-approved.
		assertThat(AgentRunner.preApproved(ruleRun(30), tool("start_screening"),
			AgentToolResult.ok(Map.of(), "agent.step.start_screening", Map.of(), List.of()))).isFalse();
	}

	@Test
	void profileUpdateRequestsWithinTheirCapRunWithoutAsking() {
		var run = ruleRun(null);
		run.setAutoApproveProfileUpdatesMax(10);
		var tool = tool("request_profile_update");
		assertThat(AgentRunner.preApproved(run, tool, requests(10))).isTrue();
		assertThat(AgentRunner.preApproved(run, tool, requests(11))).isFalse();
		// Each pre-approval covers its own tool only.
		assertThat(AgentRunner.preApproved(run, tool("start_screening"), card(1))).isFalse();
		assertThat(AgentRunner.preApproved(ruleRun(50), tool, requests(1))).isFalse();
		var chat = ruleRun(null);
		chat.setAutoApproveProfileUpdatesMax(10);
		chat.setOrigin(AgentRun.ORIGIN_CHAT);
		assertThat(AgentRunner.preApproved(chat, tool, requests(1))).isFalse();
	}

	private static AgentToolResult requests(int toSend) {
		return AgentToolResult.ok(Map.of("toSend", toSend), "agent.step.request_profile_update", Map.of(), List.of());
	}
}
