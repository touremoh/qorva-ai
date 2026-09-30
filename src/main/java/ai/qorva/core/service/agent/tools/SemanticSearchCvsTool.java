package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.mapper.CVMapper;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vector search over the tenant's CVs from a free-text description. Unlike
 * ResumeVectorSearchService, a failure is reported as such, so the model never mistakes an
 * outage for an empty library.
 */
@Slf4j
@Component
public class SemanticSearchCvsTool implements AgentTool {

	private final EmbeddingModel embeddingModel;
	private final CVRepository cvRepository;
	private final CVMapper cvMapper;

	public SemanticSearchCvsTool(EmbeddingModel embeddingModel, CVRepository cvRepository, CVMapper cvMapper) {
		this.embeddingModel = embeddingModel;
		this.cvRepository = cvRepository;
		this.cvMapper = cvMapper;
	}

	@Override
	public String name() {
		return "semantic_search_cvs";
	}

	@Override
	public String description() {
		return "Find the candidates closest to a free-text description (e.g. 'backend engineer who scaled payments "
			+ "systems'), best match first. No total count: use search_cvs for counts.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "description":{"type":"string","description":"The profile you are looking for"},
			  "limit":{"type":"integer","minimum":1,"maximum":20}
			},"required":["description"],"additionalProperties":false}""";
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
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
		var description = ToolArgs.text(args, "description");
		if (description == null) return AgentToolResult.error("description is required");
		int limit = ToolArgs.integer(args, "limit", 10, 1, 20);
		try {
			float[] embedding = embeddingModel.embed(description);
			var cvs = cvRepository.similaritySearch(embedding, new ObjectId(ctx.tenantId()), null, List.of(), limit)
				.stream().map(cvMapper::map).toList();
			var data = new LinkedHashMap<String, Object>();
			data.put("candidates", cvs.stream().map(CvProjections::summary).toList());
			var links = cvs.stream().map(cv -> new AgentRun.Link("CV", cv.getId(), CvProjections.name(cv))).toList();
			return AgentToolResult.ok(data, "agent.step.semantic_search_cvs", Map.of("count", String.valueOf(cvs.size())), links);
		} catch (RuntimeException e) {
			log.warn("semantic_search_cvs failed for tenant={}: {}", ctx.tenantId(), e.getMessage());
			return AgentToolResult.error("Semantic search is unavailable right now; use search_cvs instead.");
		}
	}
}
