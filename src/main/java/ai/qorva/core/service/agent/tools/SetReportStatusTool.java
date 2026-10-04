package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.enums.ReportStatusChannel;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.MatchingReportService;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Moves up to 25 candidates to a pipeline status on their job. Internal and reversible (the recruiter can set it
 * back), hence no approval. The change is recorded as the run's ({@code copilot:<runId>}), which keeps it from
 * firing a status rule — the loop guard.
 */
@Component
public class SetReportStatusTool implements AgentTool {

	static final int MAX_REPORTS = 25;

	private final MatchingReportService matchingReportService;

	public SetReportStatusTool(MatchingReportService matchingReportService) {
		this.matchingReportService = matchingReportService;
	}

	@Override
	public String name() {
		return "set_report_status";
	}

	@Override
	public String description() {
		return "Set where up to 25 candidates stand on their job (one status per matching report): NEW, CONTACTED, "
			+ "SHORTLISTED, INTERVIEWING, OFFERED, HIRED, REJECTED or WITHDRAWN. Only change what the recruiter asked for.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "reportIds":{"type":"array","items":{"type":"string"},"minItems":1,"maxItems":25},
			  "status":{"type":"string","enum":["NEW","CONTACTED","SHORTLISTED","INTERVIEWING","OFFERED","HIRED","REJECTED","WITHDRAWN"]}
			},"required":["reportIds","status"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.WRITE_INTERNAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.MODIFY_REPORT);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var ids = ToolArgs.list(args, "reportIds").stream().distinct().toList();
		var status = ApplicationStatusEnum.parse(ToolArgs.text(args, "status"));
		if (ids.isEmpty() || status.isEmpty()) return AgentToolResult.error("reportIds and a valid status are required");
		if (ids.size() > MAX_REPORTS) return AgentToolResult.error("At most " + MAX_REPORTS + " reports per call");

		var actor = ctx.runId() != null
			? MatchingReportService.StatusActor.copilotRun(ctx.runId())
			: new MatchingReportService.StatusActor(ctx.userEmail(), ctx.userEmail());
		var changed = new ArrayList<AgentRun.Link>();
		var notFound = new ArrayList<String>();
		for (var id : ids) {
			MatchingReportDTO report;
			try {
				report = matchingReportService.changeStatus(ctx.tenantId(), id, status.get().getStatus(), actor, ReportStatusChannel.COPILOT);
			} catch (QorvaException e) {
				notFound.add(id);
				continue;
			}
			var name = report.getCandidateInfo() != null ? report.getCandidateInfo().getCandidateName() : null;
			changed.add(new AgentRun.Link("REPORT", report.getId(), name));
		}

		var data = new LinkedHashMap<String, Object>();
		data.put("status", status.get().getStatus());
		data.put("updated", changed.stream().map(AgentRun.Link::getId).toList());
		data.put("notFound", notFound);
		return AgentToolResult.ok(data, "agent.step.set_report_status",
			Map.of("count", String.valueOf(changed.size()), "status", status.get().getStatus()), changed);
	}
}
