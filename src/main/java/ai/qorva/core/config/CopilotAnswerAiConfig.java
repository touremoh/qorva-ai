package ai.qorva.core.config;

import ai.qorva.core.service.ai.AiCallMetrics;
import ai.qorva.core.service.ai.AiCallMetricsAdvisor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The model client for Copilot's answer engines (candidate answers, library analyses). They run in the agent worker,
 * not in a browser request, so no hosting limit applies: one long answer (an interview plan) may take up to
 * {@code qorva.ai.copilot-answers.timeout-seconds} (120 s by default). A read timeout is not retried — the same long
 * answer would time out again and double the wait; rate limits and server errors (transient) are retried once.
 */
@Configuration
public class CopilotAnswerAiConfig {

	static final int ATTEMPTS = 2;
	static final Duration BACKOFF = Duration.ofSeconds(1);

	@Bean
	ChatClient copilotAnswerChatClient(ChatModel chatModel, ObjectProvider<OpenAiApi> openAiApi, CloseableHttpClient qorvaHttpClient,
	                                   AiCallMetrics aiCallMetrics,
	                                   @Value("${qorva.ai.copilot-answers.timeout-seconds:120}") int timeoutSeconds) {
		var model = chatModel instanceof OpenAiChatModel openAi && openAiApi.getIfAvailable() != null
			? withTimeout(openAi, openAiApi.getObject(), qorvaHttpClient, Duration.ofSeconds(timeoutSeconds))
			// Not OpenAI (a test double): nothing to time out, use it as it is.
			: chatModel;
		return ChatClient.builder(model)
			.defaultSystem(InteractiveAiConfig.DEFAULT_SYSTEM)
			.defaultAdvisors(new AiCallMetricsAdvisor(aiCallMetrics))
			.build();
	}

	/** Retries what may pass on a second try (429, 5xx), never a read timeout. */
	static RetryTemplate retryTemplate() {
		return RetryTemplate.builder()
			.maxAttempts(ATTEMPTS)
			.fixedBackoff(BACKOFF.toMillis())
			.retryOn(TransientAiException.class)
			.build();
	}

	private static ChatModel withTimeout(OpenAiChatModel openAiChatModel, OpenAiApi openAiApi, CloseableHttpClient qorvaHttpClient,
	                                     Duration readTimeout) {
		var requestFactory = new HttpComponentsClientHttpRequestFactory(qorvaHttpClient);
		requestFactory.setReadTimeout(readTimeout);
		var api = openAiApi.mutate()
			.restClientBuilder(RestClient.builder().requestFactory(requestFactory))
			.build();
		return openAiChatModel.mutate().openAiApi(api).retryTemplate(retryTemplate()).build();
	}
}
