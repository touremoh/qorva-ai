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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class ListJobsTool implements AgentTool {

	private final JobPostService jobPostService;

	public ListJobsTool(JobPostService jobPostService) {
		this.jobPostService = jobPostService;
	}

	@Override
	public String name() {
		return "list_jobs";
	}

	@Override
	public String description() {
		return "List job posts, most recently updated first, optionally by status or title.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "status":{"type":"string","enum":["open","closed"]},
			  "title":{"type":"string","description":"Title contains"},
			  "page":{"type":"integer","minimum":0},
			  "pageSize":{"type":"integer","minimum":1,"maximum":25}
			},"additionalProperties":false}""";
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
		var params = new HashMap<String, String>();
		params.put("tenantId", ctx.tenantId());
		var status = ToolArgs.text(args, "status");
		if (status != null) params.put("status", status);
		var title = ToolArgs.text(args, "title");
		if (title != null) params.put("title", title);
		params.put("pageNumber", String.valueOf(ToolArgs.integer(args, "page", 0, 0, 1000)));
		params.put("pageSize", String.valueOf(ToolArgs.integer(args, "pageSize", 10, 1, 25)));

		var page = jobPostService.findAll(params);
		var data = new LinkedHashMap<String, Object>();
		data.put("total", page.getTotalElements());
		data.put("jobs", page.getContent().stream().map(JobProjections::summary).toList());
		var links = page.getContent().stream().limit(10).map(j -> new AgentRun.Link("JOB", j.getId(), j.getTitle())).toList();
		return AgentToolResult.ok(data, "agent.step.list_jobs", Map.of("count", String.valueOf(page.getTotalElements())), links);
	}
}
