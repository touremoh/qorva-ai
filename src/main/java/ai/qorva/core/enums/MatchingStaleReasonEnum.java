package ai.qorva.core.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Why a job's matching results are out of date, strongest first: a weaker reason never replaces a stronger
 * one, so "job changed" is not hidden by a later "new candidates". Stored on the job by name.
 */
public enum MatchingStaleReasonEnum {
	/** Never matched — every result would be new. */
	NEVER_RUN,
	/** Title, description or scoring rules changed since the last run — every score may move. */
	JOB_CHANGED,
	/** New or changed candidates would now enter the job's top N. */
	NEW_CANDIDATES,
	/** A candidate already in the results was edited, so their report is out of date. */
	CANDIDATE_CHANGED;

	/** The reasons this one may replace: itself (refreshes the job) and every weaker one. */
	public List<String> replaces() {
		return Arrays.stream(values()).filter(r -> r.ordinal() >= ordinal()).map(Enum::name).toList();
	}
}
