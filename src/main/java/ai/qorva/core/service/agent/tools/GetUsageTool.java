package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dto.common.UsageFeatureMetrics;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The plan's allowances for the current period, so the agent can warn before spending quota. */
@Component
public class GetUsageTool implements AgentTool {

	private final UsageMonitoringService usageMonitoringService;

	public GetUsageTool(UsageMonitoringService usageMonitoringService) {
		this.usageMonitoringService = usageMonitoringService;
	}

	@Override
	public String name() {
		return "get_usage";
	}

	@Override
	public String description() {
		return "Get the plan's allowances for the current billing period (limit and consumed per meter). "
			+ "screeningActions are spent by matching runs, one per candidate scored.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{},"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.READ;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.VIEW_DASHBOARD);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
		var period = usageMonitoringService.findCurrentPeriodByTenantId(ctx.tenantId());
		if (period.isEmpty() || period.get().getFeatures() == null) {
			return AgentToolResult.ok(Map.of("metered", false), "agent.step.get_usage", Map.of(), List.of());
		}
		var usage = period.get();
		var features = usage.getFeatures();
		var meters = new LinkedHashMap<String, Object>();
		meters.put("screeningActions", meter(features.getScreeningActions()));
		meters.put("aiResumeChats", meter(features.getAiResumeChats()));
		meters.put("talentIntelligenceQueries", meter(features.getTalentIntelligenceQueries()));
		meters.put("agentRuns", meter(features.getAgentRuns()));
		var data = new LinkedHashMap<String, Object>();
		data.put("tier", usage.getSubscriptionTier());
		data.put("periodEnd", usage.getCurrentPeriodEnd() != null ? usage.getCurrentPeriodEnd().toString() : null);
		data.put("meters", meters);
		return AgentToolResult.ok(data, "agent.step.get_usage", Map.of(), List.of());
	}

	private static Map<String, Object> meter(UsageFeatureMetrics metrics) {
		var out = new LinkedHashMap<String, Object>();
		out.put("limit", metrics != null ? metrics.getLimit() : null);
		out.put("consumed", metrics != null && metrics.getConsumed() != null ? metrics.getConsumed() : 0);
		return out;
	}
}
