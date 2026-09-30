package ai.qorva.core.service.agent;

import ai.qorva.core.service.QorvaApiAccessManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Every {@link AgentTool} bean, filtered down to what a given user may use right now. */
@Slf4j
@Component
public class AgentToolRegistry {

	private final List<AgentTool> tools;
	private final QorvaApiAccessManager accessManager;

	public AgentToolRegistry(List<AgentTool> tools, QorvaApiAccessManager accessManager) {
		this.tools = tools.stream().sorted(Comparator.comparing(AgentTool::name)).toList();
		this.accessManager = accessManager;
	}

	public List<AgentTool> allowedFor(AgentToolContext ctx) {
		return tools.stream().filter(tool -> isAllowed(tool, ctx)).toList();
	}

	/** Re-checked at execution: authorities may change between the offer and the call. */
	public Optional<AgentTool> allowed(String name, AgentToolContext ctx) {
		return tools.stream().filter(t -> t.name().equals(name)).findFirst().filter(t -> isAllowed(t, ctx));
	}

	private boolean isAllowed(AgentTool tool, AgentToolContext ctx) {
		return tool.requiredActions().stream()
			.allMatch(action -> accessManager.hasPermission(ctx.authentication(), action.getValue()))
			&& isAvailable(tool, ctx);
	}

	/** An availability check that fails (e.g. the mailbox lookup) hides that tool; it must not fail the run. */
	private static boolean isAvailable(AgentTool tool, AgentToolContext ctx) {
		try {
			return tool.available(ctx);
		} catch (RuntimeException e) {
			log.warn("agent tool {} availability check failed; tool hidden: {}", tool.name(), e.toString());
			return false;
		}
	}
}
