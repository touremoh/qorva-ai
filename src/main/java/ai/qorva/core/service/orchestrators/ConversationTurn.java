package ai.qorva.core.service.orchestrators;

/** One earlier message of a Copilot conversation, as replayed to the candidate answer engine. */
public record ConversationTurn(boolean fromRecruiter, String text) {

	public static ConversationTurn recruiter(String text) {
		return new ConversationTurn(true, text);
	}

	public static ConversationTurn assistant(String text) {
		return new ConversationTurn(false, text);
	}
}
