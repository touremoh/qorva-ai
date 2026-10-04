package ai.qorva.core.service.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCallMetricsTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final AiCallMetrics metrics = new AiCallMetrics(registry);

	private long count(String agent, String outcome) {
		var timer = registry.find(AiCallMetrics.METRIC).tag("agent", agent).tag("outcome", outcome).timer();
		return timer == null ? 0 : timer.count();
	}

	@Test
	void aSuccessfulCallIsTimedAsOk() {
		assertThat(metrics.record("resume_chat", "gpt", () -> "answer")).isEqualTo("answer");
		assertThat(count("resume_chat", AiCallMetrics.OK)).isEqualTo(1);
	}

	@Test
	void aTimeoutIsCountedAsSuchAndRethrownAsAnAiFailureKeepingTheCause() {
		var timeout = new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

		assertThatThrownBy(() -> metrics.record("insight_answer", "gpt", () -> { throw timeout; }))
			.isInstanceOf(AiCallFailedException.class)
			.hasCause(timeout)
			.extracting(e -> ((AiCallFailedException) e).getOutcome()).isEqualTo(AiCallMetrics.TIMEOUT);
		assertThat(count("insight_answer", AiCallMetrics.TIMEOUT)).isEqualTo(1);
	}

	@Test
	void aRequestTheModelRejectsIsRefusedAndAnythingElseIsAnError() {
		assertThat(AiCallMetrics.outcome(new NonTransientAiException("400 bad request"))).isEqualTo(AiCallMetrics.REFUSED);
		assertThat(AiCallMetrics.outcome(new IllegalStateException("boom"))).isEqualTo(AiCallMetrics.ERROR);
	}

	@Test
	void aFailureAlreadyRecordedByAnInnerCallIsNotCountedTwice() {
		var inner = new AiCallFailedException("chat", AiCallMetrics.ERROR, new IllegalStateException());

		assertThatThrownBy(() -> metrics.record("outer", "gpt", () -> { throw inner; })).isSameAs(inner);
		assertThat(count("outer", AiCallMetrics.ERROR)).isZero();
	}
}
