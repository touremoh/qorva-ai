package ai.qorva.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CopilotAnswerAiConfigTest {

	@Test
	void aReadTimeoutIsNotRetried() {
		var calls = new AtomicInteger();

		assertThatThrownBy(() -> CopilotAnswerAiConfig.retryTemplate().execute(ctx -> {
			calls.incrementAndGet();
			throw new ResourceAccessException("Read timed out");
		})).isInstanceOf(ResourceAccessException.class);

		assertThat(calls).hasValue(1);
	}

	@Test
	void aTransientErrorIsRetriedOnce() {
		var calls = new AtomicInteger();

		assertThatThrownBy(() -> CopilotAnswerAiConfig.retryTemplate().execute(ctx -> {
			calls.incrementAndGet();
			throw new TransientAiException("429");
		})).isInstanceOf(TransientAiException.class);

		assertThat(calls).hasValue(CopilotAnswerAiConfig.ATTEMPTS);
	}
}
