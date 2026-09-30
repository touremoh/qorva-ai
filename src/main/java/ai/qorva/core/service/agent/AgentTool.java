package ai.qorva.core.service.agent;

import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * One capability offered to the model. Implementations are thin adapters over existing services:
 * the tenant comes from the run's scope, never from the arguments, and every id is looked up with a
 * tenant-scoped query, so an id taken from model output can't reach another tenant's data.
 */
public interface AgentTool {

	/** snake_case name the model calls. */
	String name();

	/** What the tool does, for the model. */
	String description();

	/** JSON schema of the arguments. */
	String inputSchema();

	AgentRiskTier tier();

	/** The user must hold all of these for the tool to be offered and executed. */
	Set<UserActionsEnum> requiredActions();

	/** Extra availability condition beyond authorities (e.g. a connected mailbox). */
	default boolean available(AgentToolContext ctx) {
		return true;
	}

	AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException;
}
