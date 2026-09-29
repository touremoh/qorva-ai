package ai.qorva.core.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * AI-written reading of the tenant's usage for the current billing period: which meter to watch
 * and how to consume less, in the viewer's language. Generated once in English per usage state,
 * then translated, so every user of a tenant gets the same advice.
 */
public record UsageInsight(
	String headline,
	String explanation,
	List<Recommendation> recommendations,
	String language,
	Instant generatedAt
) {

	/** One action; {@code feature} names the meter it saves (screeningActions, aiResumeChats, talentIntelligenceQueries, agentRuns), or null. */
	public record Recommendation(String text, String feature) {}

	/** What the model writes (English). */
	public record Draft(String headline, String explanation, List<DraftRecommendation> recommendations) {}

	public record DraftRecommendation(String text, String feature) {}

	/** Everything the model is told: the period, each meter with its pace, and what drives consumption. */
	public record Input(
		String tier,
		String billingCycle,
		String periodStart,
		String periodEnd,
		long daysLeft,
		Map<String, Meter> meters,
		Drivers drivers
	) {}

	public record Meter(Integer limit, int consumed, Integer percentUsed, UsageForecast.Status pace,
	                    Long projectedAtPeriodEnd, String limitReachedOn) {}

	public record Drivers(long openJobs, long openJobsAwaitingMatching, long resumesAddedThisPeriod,
	                      int candidatesScoredPerJobPerMatchingRun) {}
}
