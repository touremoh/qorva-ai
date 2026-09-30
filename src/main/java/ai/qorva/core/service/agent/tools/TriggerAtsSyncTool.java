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
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.service.ats.AtsConnectionService;
import ai.qorva.core.service.ats.AtsSyncService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Starts an import from a connected ATS (new candidates, and jobs if enabled) — after the recruiter approves. */
@Component
public class TriggerAtsSyncTool implements AgentTool {

	private final AtsConnectionService connectionService;
	private final AtsSyncService syncService;

	public TriggerAtsSyncTool(AtsConnectionService connectionService, AtsSyncService syncService) {
		this.connectionService = connectionService;
		this.syncService = syncService;
	}

	@Override
	public String name() {
		return "trigger_ats_sync";
	}

	@Override
	public String description() {
		return "Start an import from a connected ATS (use list_ats_connections for the id). It runs in the background "
			+ "and can use matching actions for each imported resume, so the recruiter approves it first.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{"connectionId":{"type":"string"}},"required":["connectionId"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.APPROVAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.MANAGE_INTEGRATIONS);
	}

	@Override
	public AgentToolResult preview(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var id = ToolArgs.text(args, "connectionId");
		if (id == null) return AgentToolResult.error("connectionId is required");
		AtsConnection connection;
		try {
			connection = connectionService.findOwned(ctx.tenantId(), id);
		} catch (QorvaException e) {
			return AgentToolResult.error("Unknown ATS connection: " + id);
		}
		if (!AtsConnection.STATUS_CONNECTED.equals(connection.getStatus())) {
			return AgentToolResult.error("This ATS connection is not connected (status " + connection.getStatus() + ").");
		}
		var card = new LinkedHashMap<String, Object>();
		card.put("connectionId", connection.getId());
		card.put("provider", connection.getProvider());
		card.put("name", label(connection));
		return AgentToolResult.ok(card, "agent.step.trigger_ats_sync", Map.of("name", label(connection)), List.of());
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
		return AgentToolResult.error("Starting an ATS import needs the recruiter's approval.");
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx, AgentApproval approval) throws QorvaException {
		var id = ToolArgs.text(args, "connectionId");
		var connection = connectionService.findOwned(ctx.tenantId(), id);
		var job = syncService.enqueue(ctx.tenantId(), connection.getId(), AtsSyncService.TRIGGER_MANUAL, ctx.userEmail());
		var data = new LinkedHashMap<String, Object>();
		data.put("started", true);
		data.put("syncJobId", job.id());
		data.put("note", "The import runs in the background; new candidates appear in the library as they arrive.");
		return AgentToolResult.ok(data, "agent.step.ats_sync_started", Map.of("name", label(connection)), List.of());
	}

	private static String label(AtsConnection connection) {
		return connection.getDisplayName() != null && !connection.getDisplayName().isBlank()
			? connection.getDisplayName() : connection.getProvider();
	}
}
