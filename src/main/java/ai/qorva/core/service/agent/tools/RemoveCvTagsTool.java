package ai.qorva.core.service.agent.tools;

import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RemoveCvTagsTool implements AgentTool {

	private final CVService cvService;

	public RemoveCvTagsTool(CVService cvService) {
		this.cvService = cvService;
	}

	@Override
	public String name() {
		return "remove_cv_tags";
	}

	@Override
	public String description() {
		return "Remove tags from up to 25 candidates. Only remove what the recruiter asked for.";
	}

	@Override
	public String inputSchema() {
		return CvTagChange.SCHEMA;
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.WRITE_INTERNAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.MODIFY_CV);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		return CvTagChange.apply(cvService, args, "agent.step.remove_cv_tags", CvTagChange::removed);
	}
}
