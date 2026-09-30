package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.agent.AgentApproval;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ai.qorva.core.service.ats.AtsConnectionService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ListAtsConnectionsTool implements AgentTool {

	private final AtsConnectionService connectionService;

	public ListAtsConnectionsTool(AtsConnectionService connectionService) {
		this.connectionService = connectionService;
	}

	@Override
	public String name() {
		return "list_ats_connections";
	}

	@Override
	public String description() {
		return "List the ATS integrations of the account (id, provider, name, status).";
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
		return Set.of(UserActionsEnum.MANAGE_INTEGRATIONS);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
		var connections = connectionService.list(ctx.tenantId()).connections().stream().map(c -> {
			var row = new LinkedHashMap<String, Object>();
			row.put("connectionId", c.id());
			row.put("provider", c.provider());
			row.put("name", c.displayName());
			row.put("status", c.status());
			return row;
		}).toList();
		return AgentToolResult.ok(Map.of("connections", connections), "agent.step.list_ats_connections",
			Map.of("count", String.valueOf(connections.size())), List.<AgentRun.Link>of());
	}
}
