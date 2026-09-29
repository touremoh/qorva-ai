package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Exact filters over the CV library — the same query the resume list uses. */
@Component
public class SearchCvsTool implements AgentTool {

	private final CVService cvService;

	public SearchCvsTool(CVService cvService) {
		this.cvService = cvService;
	}

	@Override
	public String name() {
		return "search_cvs";
	}

	@Override
	public String description() {
		return "Search the CV library with exact filters and get a page of candidates with the total count. "
			+ "Use it for counts and structured criteria (skills, seniority, location, tags, dates). "
			+ "For fuzzy descriptions use semantic_search_cvs.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "text":{"type":"string","description":"Free text matched against name, role and skills"},
			  "role":{"type":"string","description":"Current role or past position contains this"},
			  "skills":{"type":"array","items":{"type":"string"},"description":"Candidate must have ALL of these skills"},
			  "seniority":{"type":"array","items":{"type":"string","enum":["junior","midLevel","senior","lead","principal","manager","director","executive"]}},
			  "locations":{"type":"array","items":{"type":"string"},"description":"City or country, any of"},
			  "industries":{"type":"array","items":{"type":"string"},"description":"Industry domains, any of"},
			  "tags":{"type":"array","items":{"type":"string"},"description":"Tags, any of"},
			  "minYearsOfExperience":{"type":"integer"},
			  "maxYearsOfExperience":{"type":"integer"},
			  "createdAfter":{"type":"string","description":"ISO date, e.g. 2026-09-01: CVs added since"},
			  "archived":{"type":"boolean","description":"true to search archived CVs instead of active ones"},
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
		return Set.of(UserActionsEnum.VIEW_CV);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var params = new HashMap<String, String>();
		params.put("tenantId", ctx.tenantId());
		putIfPresent(params, "q", ToolArgs.text(args, "text"));
		putIfPresent(params, "role", ToolArgs.text(args, "role"));
		putIfPresent(params, "skills", ToolArgs.csv(args, "skills"));
		putIfPresent(params, "seniority", ToolArgs.csv(args, "seniority"));
		putIfPresent(params, "locations", ToolArgs.csv(args, "locations"));
		putIfPresent(params, "industries", ToolArgs.csv(args, "industries"));
		putIfPresent(params, "tags", ToolArgs.csv(args, "tags"));
		var min = ToolArgs.optionalInteger(args, "minYearsOfExperience");
		var max = ToolArgs.optionalInteger(args, "maxYearsOfExperience");
		putIfPresent(params, "minYearsOfExperience", min != null ? min.toString() : null);
		putIfPresent(params, "maxYearsOfExperience", max != null ? max.toString() : null);
		putIfPresent(params, "createdAfter", ToolArgs.text(args, "createdAfter"));
		if (args != null && args.path("archived").asBoolean(false)) {
			params.put("archived", "true");
		}
		params.put("pageNumber", String.valueOf(ToolArgs.integer(args, "page", 0, 0, 1000)));
		params.put("pageSize", String.valueOf(ToolArgs.integer(args, "pageSize", 10, 1, 25)));

		var page = cvService.findAll(params);
		var data = new LinkedHashMap<String, Object>();
		data.put("total", page.getTotalElements());
		data.put("page", page.getNumber());
		data.put("candidates", page.getContent().stream().map(CvProjections::summary).toList());
		var links = page.getContent().stream().limit(10)
			.map(cv -> new AgentRun.Link("CV", cv.getId(), CvProjections.name(cv)))
			.toList();
		return AgentToolResult.ok(data, "agent.step.search_cvs", Map.of("count", String.valueOf(page.getTotalElements())), links);
	}

	private static void putIfPresent(Map<String, String> params, String key, String value) {
		if (value != null) params.put(key, value);
	}
}
