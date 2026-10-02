package ai.qorva.core.service.agent.rules;

/** Small text helpers shared by the trigger sources and the goal renderer. */
final class RuleText {

	static final int MAX_NAME = 80;

	private RuleText() {}

	/**
	 * A record's name as it goes into a rule run's message: one line, bounded. Names come from candidates'
	 * documents, so they must not be able to add lines (instructions) to the message.
	 */
	static String name(String value) {
		if (value == null || value.isBlank()) return "(unnamed)";
		var oneLine = value.replaceAll("\\s+", " ").strip();
		return oneLine.length() <= MAX_NAME ? oneLine : oneLine.substring(0, MAX_NAME - 1) + "…";
	}
}
