package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ai.qorva.core.service.MatchingReportService;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GetReportTool implements AgentTool {

	private final MatchingReportService matchingReportService;

	public GetReportTool(MatchingReportService matchingReportService) {
		this.matchingReportService = matchingReportService;
	}

	@Override
	public String name() {
		return "get_report";
	}

	@Override
	public String description() {
		return "Get one matching report in full: score, verdict, strengths, weaknesses, missing skills and red flags.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{"reportId":{"type":"string"}},"required":["reportId"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.READ;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.VIEW_REPORT);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var id = ToolArgs.text(args, "reportId");
		if (id == null) return AgentToolResult.error("reportId is required");
		var report = matchingReportService.findOneById(id);
		var details = report.getMatchingReportDetails();
		var data = ReportProjections.summary(report.getId(), report.getJobPostId(), report.getJobPostTitle(),
			report.getCandidateInfo(), details, report.getOutdated(), report.getStatus());
		if (details != null) {
			data.put("statusHistory", report.getStatusHistory());
			data.put("decision", details.getDecisionSummary());
			data.put("strengths", details.getStrengths());
			data.put("weaknesses", details.getWeaknesses());
			data.put("missingSkills", details.getMissingSkills());
			data.put("redFlags", details.getRedFlags());
		}
		var name = report.getCandidateInfo() != null ? report.getCandidateInfo().getCandidateName() : "";
		return AgentToolResult.ok(data, "agent.step.get_report", Map.of("name", name != null ? name : ""),
			List.of(new AgentRun.Link("REPORT", report.getId(), name)));
	}
}
