package ai.qorva.core.service.agent;

/** The run can't continue on behalf of its user; {@code reason} is an error.* key shown by the app. */
public class AgentPreconditionException extends RuntimeException {

	private final String reason;

	public AgentPreconditionException(String reason, String message) {
		super(message);
		this.reason = reason;
	}

	public String reason() {
		return reason;
	}
}
