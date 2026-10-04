package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.AgentData;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.service.agent.AgentApproval;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.rules.AgentRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProposeRuleToolTest {

	private static final String TENANT = "64b7f0f0f0f0f0f0f0f0f0f0";
	private static final AgentToolContext CHAT = new AgentToolContext(TENANT, "owner@a.test", "en", null, "run-1",
		AgentRun.ORIGIN_CHAT, "Europe/Paris");
	private static final AgentToolContext RULE_RUN = new AgentToolContext(TENANT, "owner@a.test", "en", null, "run-2",
		AgentRun.ORIGIN_RULE, null);
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String ARGS = """
		{"name":"Monday digest","goal":"Summarise the best new matches.","trigger":{"type":"SCHEDULE","frequency":"WEEKLY","hour":8,"weekday":1}}""";

	@Mock private AgentRuleService ruleService;

	@Test
	void itIsOfferedInChatOnlyNeverToARunAStandingRuleStarted() {
		when(ruleService.rulesEnabled()).thenReturn(true);
		var tool = new ProposeRuleTool(ruleService);
		assertThat(tool.available(CHAT)).isTrue();
		assertThat(tool.available(RULE_RUN)).isFalse();
		when(ruleService.rulesEnabled()).thenReturn(false);
		assertThat(tool.available(CHAT)).isFalse();
	}

	@Test
	void theCardShowsTheCheckedRuleInTheRecruitersTimeZoneAndCreatesNothing() throws Exception {
		var validated = new AgentRule();
		var trigger = new AgentRule.Trigger();
		trigger.setType(AgentRule.TRIGGER_SCHEDULE);
		trigger.setZoneId("Europe/Paris");
		validated.setTrigger(trigger);
		var captor = ArgumentCaptor.forClass(AgentData.RuleRequest.class);
		when(ruleService.validate(eq(TENANT), eq("owner@a.test"), eq("en"), captor.capture())).thenReturn(validated);

		var preview = new ProposeRuleTool(ruleService).preview(JSON.readTree(ARGS), CHAT);

		assertThat(preview.ok()).isTrue();
		assertThat(captor.getValue().getTrigger().getZoneId()).isEqualTo("Europe/Paris");
		assertThat(captor.getValue().getTrigger().getWeekday()).isEqualTo(1);
		assertThat(((Map<?, ?>) preview.data()).get("name")).isEqualTo("Monday digest");
		verify(ruleService, never()).create(any(), any(), any(), any());
	}

	@Test
	void anInvalidRuleGoesBackToTheModelInsteadOfACard() throws Exception {
		when(ruleService.validate(any(), any(), any(), any())).thenThrow(QorvaErrors.badRequest(QorvaErrorCodes.AGENT_RULE_INVALID));
		var preview = new ProposeRuleTool(ruleService).preview(JSON.readTree(ARGS), CHAT);
		assertThat(preview.ok()).isFalse();
	}

	@Test
	void approvalCreatesTheRuleAsTheRecruiter() throws Exception {
		var view = new AgentData.RuleView("r1", "Monday digest", "owner@a.test", null, "g", 20, false, null, "ACTIVE", null, 0, 0,
			null, null, null, null, true, true);
		when(ruleService.create(eq(TENANT), eq("owner@a.test"), eq("en"), any())).thenReturn(view);
		var result = new ProposeRuleTool(ruleService).execute(JSON.readTree(ARGS), CHAT, new AgentApproval(null, null));
		assertThat(result.ok()).isTrue();
		assertThat(((Map<?, ?>) result.data()).get("ruleId")).isEqualTo("r1");
	}
}
