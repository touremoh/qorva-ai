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
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.service.JobPostService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Title, description and open/closed status of an existing job post; nothing else. */
@Component
public class UpdateJobPostTool implements AgentTool {

	private static final Set<String> STATUSES = Set.of("open", "closed");

	private final JobPostService jobPostService;

	public UpdateJobPostTool(JobPostService jobPostService) {
		this.jobPostService = jobPostService;
	}

	@Override
	public String name() {
		return "update_job_post";
	}

	@Override
	public String description() {
		return "Change a job post's title, description (plain text) or status (open/closed). Fields left out are kept. "
			+ "Only what the recruiter asked to change.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "jobId":{"type":"string"},
			  "title":{"type":"string","maxLength":200},
			  "description":{"type":"string"},
			  "status":{"type":"string","enum":["open","closed"]}
			},"required":["jobId"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.WRITE_INTERNAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.MODIFY_JOB);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var jobId = ToolArgs.text(args, "jobId");
		if (jobId == null) return AgentToolResult.error("jobId is required");
		var title = ToolArgs.text(args, "title");
		var description = ToolArgs.text(args, "description");
		var status = ToolArgs.text(args, "status");
		if (title == null && description == null && status == null) return AgentToolResult.error("Nothing to change");
		if (title != null && title.length() > 200) return AgentToolResult.error("title is at most 200 characters");
		if (description != null && description.length() > JobDescriptions.MAX_LENGTH) return AgentToolResult.error("description is too long");
		if (status != null && !STATUSES.contains(status)) return AgentToolResult.error("status is open or closed");

		var existing = jobPostService.findOneById(jobId);
		var patch = new JobPostDTO();
		patch.setTitle(title);
		patch.setDescription(description != null ? JobDescriptions.toHtml(description) : null);
		patch.setStatus(status);
		var updated = jobPostService.updateOne(existing.getId(), patch);
		return AgentToolResult.ok(JobProjections.summary(updated), "agent.step.update_job_post",
			Map.of("title", updated.getTitle() != null ? updated.getTitle() : ""),
			List.of(new AgentRun.Link("JOB", updated.getId(), updated.getTitle())));
	}
}
