package ai.qorva.core.dto;

import java.time.Instant;
import java.util.List;

/**
 * AI-written reading of the tenant's Library Quality report: what the numbers say and what to do
 * next, in the viewer's language. Generated once in English per report state, then translated, so
 * every user of a tenant gets the same advice whatever their language.
 */
public record LibraryQualityInsight(
	String headline,
	String explanation,
	List<Recommendation> recommendations,
	String language,
	Instant generatedAt
) {

	/** One action to take; {@code issueKey} links it to a row of the report's issue list, or is null. */
	public record Recommendation(String text, String issueKey) {}

	/** What the model writes (English). */
	public record Draft(
		String headline,
		String explanation,
		List<DraftRecommendation> recommendations
	) {}

	public record DraftRecommendation(
		String text,
		String issueKey
	) {}
}
