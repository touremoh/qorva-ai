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
import ai.qorva.core.dto.JobDescriptionData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.service.JobDescriptionBuilderService;
import ai.qorva.core.service.JobPostService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates an open job post, as the "New job" form does. With {@code generateDescription}, the AI job
 * description builder writes the description and suggests scoring rules, exactly as in the form.
 */
@Component
public class CreateJobPostTool implements AgentTool {

	private final JobPostService jobPostService;
	private final JobDescriptionBuilderService descriptionBuilder;

	public CreateJobPostTool(JobPostService jobPostService, JobDescriptionBuilderService descriptionBuilder) {
		this.jobPostService = jobPostService;
		this.descriptionBuilder = descriptionBuilder;
	}

	@Override
	public String name() {
		return "create_job_post";
	}

	@Override
	public String description() {
		return "Create an open job post. Either give a description, or set generateDescription with the key facts "
			+ "(seniority, skills, location, contract) and the AI job description builder writes it. "
			+ "Only when the recruiter asked for a new job.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "title":{"type":"string","maxLength":200},
			  "description":{"type":"string","description":"Plain text; paragraphs separated by blank lines"},
			  "generateDescription":{"type":"boolean"},
			  "seniority":{"type":"string"},
			  "mustHaveSkills":{"type":"string"},
			  "niceToHaveSkills":{"type":"string"},
			  "location":{"type":"string"},
			  "contractType":{"type":"string"}
			},"required":["title"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.WRITE_INTERNAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.ADD_JOB);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var title = ToolArgs.text(args, "title");
		if (title == null || title.length() > 200) return AgentToolResult.error("title is required (at most 200 characters)");
		var job = new JobPostDTO();
		job.setTenantId(ctx.tenantId());
		job.setTitle(title);

		if (args != null && args.path("generateDescription").asBoolean(false)) {
			var request = new JobDescriptionData.GenerateRequest();
			request.setTitle(title);
			request.setSeniority(ToolArgs.text(args, "seniority"));
			request.setMustHaveSkills(ToolArgs.text(args, "mustHaveSkills"));
			request.setNiceToHaveSkills(ToolArgs.text(args, "niceToHaveSkills"));
			request.setLocation(ToolArgs.text(args, "location"));
			request.setContractType(ToolArgs.text(args, "contractType"));
			var generated = descriptionBuilder.generate(ctx.tenantId(), request, ctx.language());
			job.setDescription(generated.description());
			job.setScoringRules(generated.scoringRules());
		} else {
			var description = ToolArgs.text(args, "description");
			if (description == null) return AgentToolResult.error("Give a description or set generateDescription");
			if (description.length() > JobDescriptions.MAX_LENGTH) return AgentToolResult.error("description is too long");
			job.setDescription(JobDescriptions.toHtml(description));
		}

		var created = jobPostService.createOne(job);
		return AgentToolResult.ok(JobProjections.detail(created), "agent.step.create_job_post",
			Map.of("title", created.getTitle()), List.of(new AgentRun.Link("JOB", created.getId(), created.getTitle())));
	}
}
