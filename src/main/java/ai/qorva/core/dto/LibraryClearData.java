package ai.qorva.core.dto;

/** API shapes for the clear-library operation ({@code /cvs/clear-library}). */
public final class LibraryClearData {

	private LibraryClearData() {}

	/** Shown in the confirm dialog before the user commits to the wipe. */
	public record Preflight(long cvs, long reports, long chats) {}

	/** What was actually deleted. */
	public record Result(
		long cvs,
		long reports,
		long chats,
		long chatMessages,
		long insightTurns,
		long candidateUpdateRequests,
		long qualityIssueStates
	) {}
}
