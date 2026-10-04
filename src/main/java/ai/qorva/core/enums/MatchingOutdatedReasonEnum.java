package ai.qorva.core.enums;

/** Why a matching report left the job's latest results. Stored on the report by name. */
public enum MatchingOutdatedReasonEnum {
	/** Other candidates now rank higher, or the job was matched with a smaller Top N. */
	RANKED_OUT,
	/** The candidate no longer qualifies: archived, filtered out by the job's availability rules, or deleted. */
	NO_LONGER_ELIGIBLE
}
