package ai.qorva.core.service.agent;

/** How much autonomy a tool gets. Fixed per tool in code; the model never chooses it. */
public enum AgentRiskTier {
	/** Reads only; runs on its own. */
	READ,
	/** Reversible change inside Qorva; runs on its own and is recorded in the timeline. */
	WRITE_INTERNAL,
	/** Outbound or irreversible; waits for the user's approval. */
	APPROVAL
}
