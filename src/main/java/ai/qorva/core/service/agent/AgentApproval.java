package ai.qorva.core.service.agent;

/** The user's decision on an approval action: the edits they made on the card (null = unchanged). */
public record AgentApproval(String subject, String body) {

	public static final AgentApproval UNCHANGED = new AgentApproval(null, null);
}
