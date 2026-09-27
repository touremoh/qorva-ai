package ai.qorva.core.service;

import ai.qorva.core.dto.UsageForecast.Status;
import ai.qorva.core.dto.UsageMonitoringDTO;
import ai.qorva.core.dto.common.UsageFeatureMetrics;
import ai.qorva.core.dto.common.UsageFeatures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UsageForecasterTest {

	private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");
	private static final Instant END = Instant.parse("2026-10-01T00:00:00Z"); // 30 days

	private static UsageFeatureMetrics metrics(Integer limit, int consumed) {
		return UsageFeatureMetrics.builder().limit(limit).consumed(consumed).cumulative(0L).build();
	}

	private static Instant day(int n) {
		return START.plus(n, ChronoUnit.DAYS);
	}

	@Test
	void tooEarly_beforeThreeDaysOrTenPercent() {
		assertThat(UsageForecaster.forecast(metrics(1000, 50), START, END, day(2)).status()).isEqualTo(Status.TOO_EARLY);
	}

	@Test
	void onTrack_whenProjectedUnderEightyPercent() {
		var forecast = UsageForecaster.forecast(metrics(1000, 150), START, END, day(10));
		assertThat(forecast.status()).isEqualTo(Status.ON_TRACK);
		assertThat(forecast.projectedConsumed()).isEqualTo(450);
	}

	@Test
	void watch_whenProjectedBetweenEightyAndHundredPercent() {
		assertThat(UsageForecaster.forecast(metrics(1000, 300), START, END, day(10)).status()).isEqualTo(Status.WATCH);
	}

	@Test
	void willExceed_givesTheDayTheLimitIsCrossed() {
		var forecast = UsageForecaster.forecast(metrics(1000, 500), START, END, day(10));
		assertThat(forecast.status()).isEqualTo(Status.WILL_EXCEED);
		assertThat(forecast.projectedConsumed()).isEqualTo(1500);
		assertThat(forecast.limitReachedOn()).isEqualTo(day(20));
	}

	@Test
	void reached_whenTheLimitIsUsedUp() {
		assertThat(UsageForecaster.forecast(metrics(1000, 1000), START, END, day(1)).status()).isEqualTo(Status.REACHED);
	}

	@Test
	void unmetered_whenThereIsNoLimit() {
		var forecast = UsageForecaster.forecast(metrics(null, 42), START, END, day(10));
		assertThat(forecast.status()).isEqualTo(Status.UNMETERED);
		assertThat(forecast.projectedConsumed()).isNull();
	}

	@Test
	void yearlyPeriod_usesTenPercentAsTheWarmUp() {
		var yearEnd = START.plus(365, ChronoUnit.DAYS);
		assertThat(UsageForecaster.forecast(metrics(12000, 500), START, yearEnd, day(20)).status()).isEqualTo(Status.TOO_EARLY);
		assertThat(UsageForecaster.forecast(metrics(12000, 2000), START, yearEnd, day(40)).status()).isEqualTo(Status.WILL_EXCEED);
	}

	@Test
	void forecast_isKeyedLikeTheApiFeatures() {
		var usage = new UsageMonitoringDTO();
		usage.setCurrentPeriodStart(START);
		usage.setCurrentPeriodEnd(END);
		usage.setFeatures(UsageFeatures.builder()
			.screeningActions(metrics(1000, 10))
			.aiResumeChats(metrics(500, 5))
			.talentIntelligenceQueries(null)
			.build());

		assertThat(UsageForecaster.forecast(usage, day(10))).containsOnlyKeys("screeningActions", "aiResumeChats");
	}
}
