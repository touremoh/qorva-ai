package ai.qorva.core.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveAiConfigTest {

	@Test
	void bothAttemptsAndTheBackoffFitInTheBudget() {
		var perAttempt = InteractiveAiConfig.perAttemptTimeout(50);

		assertThat(perAttempt).isEqualTo(Duration.ofSeconds(24));
		assertThat(perAttempt.multipliedBy(InteractiveAiConfig.ATTEMPTS).plus(InteractiveAiConfig.BACKOFF))
			.isLessThan(Duration.ofSeconds(50));
	}

	@Test
	void aTinyBudgetStillLeavesTheModelAFewSeconds() {
		assertThat(InteractiveAiConfig.perAttemptTimeout(3)).isEqualTo(Duration.ofSeconds(5));
	}
}
