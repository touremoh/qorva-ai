package ai.qorva.core.service.ai;

/**
 * A model call failed or ran out of time (retries included). Unchecked so it passes through Spring AI's
 * advisors; the API answers it as {@code error.ai.request_failed} (503), which the app shows with a Retry.
 */
public class AiCallFailedException extends RuntimeException {

	private final String outcome;

	public AiCallFailedException(String agent, String outcome, Throwable cause) {
		super("AI call " + agent + " failed (" + outcome + ")", cause);
		this.outcome = outcome;
	}

	public String getOutcome() {
		return outcome;
	}
}
