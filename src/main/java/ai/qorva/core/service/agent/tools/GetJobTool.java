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
import ai.qorva.core.service.JobPostService;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GetJobTool implements AgentTool {

	private final JobPostService jobPostService;

	public GetJobTool(JobPostService jobPostService) {
		this.jobPostService = jobPostService;
	}

	@Override
	public String name() {
		return "get_job";
	}

	@Override
	public String description() {
		return "Get one job post with its description as plain text.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{"jobId":{"type":"string"}},"required":["jobId"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.READ;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.VIEW_JOB);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var id = ToolArgs.text(args, "jobId");
		if (id == null) return AgentToolResult.error("jobId is required");
		var job = jobPostService.findOneById(id);
		return AgentToolResult.ok(JobProjections.detail(job), "agent.step.get_job",
			Map.of("title", job.getTitle() != null ? job.getTitle() : ""), List.of(new AgentRun.Link("JOB", job.getId(), job.getTitle())));
	}
}
