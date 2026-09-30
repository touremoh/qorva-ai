package ai.qorva.core.service;

import ai.qorva.core.dto.UsageForecast;
import ai.qorva.core.dto.UsageForecast.Status;
import ai.qorva.core.dto.UsageMonitoringDTO;
import ai.qorva.core.dto.common.UsageFeatureMetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Projects each meter to the end of its billing period at the pace used so far. Linear on purpose:
 * it is shown as a hint ("at this pace…"), and a straight line is what a user can check by hand.
 */
public final class UsageForecaster {

	/** Before this much of the period has elapsed, a pace is noise. */
	static final Duration MIN_ELAPSED = Duration.ofDays(3);
	static final double MIN_ELAPSED_SHARE = 0.10;
	static final double WATCH_SHARE = 0.80;

	private UsageForecaster() {
	}

	/** Keyed like {@code features} in the API: screeningActions, aiResumeChats, talentIntelligenceQueries, agentRuns. */
	public static Map<String, UsageForecast> forecast(UsageMonitoringDTO usage, Instant now) {
		var forecasts = new LinkedHashMap<String, UsageForecast>();
		var features = usage.getFeatures();
		if (features == null || usage.getCurrentPeriodStart() == null || usage.getCurrentPeriodEnd() == null) {
			return forecasts;
		}
		put(forecasts, "screeningActions", features.getScreeningActions(), usage, now);
		put(forecasts, "aiResumeChats", features.getAiResumeChats(), usage, now);
		put(forecasts, "talentIntelligenceQueries", features.getTalentIntelligenceQueries(), usage, now);
		put(forecasts, "agentRuns", features.getAgentRuns(), usage, now);
		return forecasts;
	}

	private static void put(Map<String, UsageForecast> forecasts, String key, UsageFeatureMetrics metrics,
	                        UsageMonitoringDTO usage, Instant now) {
		if (metrics != null) {
			forecasts.put(key, forecast(metrics, usage.getCurrentPeriodStart(), usage.getCurrentPeriodEnd(), now));
		}
	}

	static UsageForecast forecast(UsageFeatureMetrics metrics, Instant start, Instant end, Instant now) {
		Integer limit = metrics.getLimit();
		long consumed = metrics.getConsumed() != null ? metrics.getConsumed() : 0;
		if (limit == null) {
			return new UsageForecast(null, null, Status.UNMETERED);
		}
		if (consumed >= limit) {
			return new UsageForecast(consumed, null, Status.REACHED);
		}
		long periodSeconds = Math.max(1, Duration.between(start, end).getSeconds());
		long elapsedSeconds = Math.min(periodSeconds, Math.max(0, Duration.between(start, now).getSeconds()));
		long minElapsed = Math.max(MIN_ELAPSED.getSeconds(), (long) (periodSeconds * MIN_ELAPSED_SHARE));
		if (elapsedSeconds < minElapsed) {
			return new UsageForecast(null, null, Status.TOO_EARLY);
		}
		double perSecond = (double) consumed / elapsedSeconds;
		long projected = Math.round(perSecond * periodSeconds);
		if (projected > limit) {
			var reachedOn = start.plusSeconds((long) Math.ceil(limit / perSecond));
			return new UsageForecast(projected, reachedOn, Status.WILL_EXCEED);
		}
		return new UsageForecast(projected, null, projected >= limit * WATCH_SHARE ? Status.WATCH : Status.ON_TRACK);
	}
}
