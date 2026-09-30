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

	/**
	 * APPROVAL tier only: checks the call and returns what the approval card shows (never acts). An error result
	 * goes back to the model instead of a card. Called when the action is proposed and again just before executing.
	 */
	default AgentToolResult preview(JsonNode args, AgentToolContext ctx) throws QorvaException {
		throw new UnsupportedOperationException(name() + " has no approval preview");
	}

	/** APPROVAL tier: executes with the edits the user made on the card. */
	default AgentToolResult execute(JsonNode args, AgentToolContext ctx, AgentApproval approval) throws QorvaException {
		return execute(args, ctx);
	}

	/** True for tools that send something to a candidate; they count against max-outbound-per-run. */
	default boolean outbound() {
		return false;
	}
}
