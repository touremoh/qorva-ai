package ai.qorva.core.dto;

import java.util.List;

/** Qorva Help API. Raw JSON, like the other newer feature controllers. */
public final class HelpData {

	private HelpData() {
	}

	public record Availability(boolean enabled) {
	}

	/** {@code page} = the key of the page the user is on (see HelpLinks), optional. */
	public record AskRequest(String conversationId, String message, String page) {
	}

	public record Link(String key) {
	}

	public record Answer(String conversationId, String answer, List<Link> links, List<String> followUps, boolean offerSupport) {
	}

	public record TicketRequest(String conversationId, String subject, String description, boolean includeConversation, String page) {
	}

	public record TicketCreated(String reference) {
	}

	/** What the model must return (JSON schema); checked by HelpAnswerGuard before anything reaches the user. */
	public record ModelAnswer(String answer, List<String> links, List<String> followUps, boolean offerSupport) {
	}
}
