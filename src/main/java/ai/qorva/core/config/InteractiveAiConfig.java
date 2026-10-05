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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * The model client for calls a browser request waits on (outreach and job description drafts, scoring-rule prefill,
 * usage and data-health summaries); Copilot's answer engines run in the worker and use {@link CopilotAnswerAiConfig}.
 * The whole call — one retry included — fits in {@code qorva.ai.interactive.timeout-seconds} (50 s by default), under the hosting's 60–120 s request limit, so a
 * slow model ends in a clear "AI request failed, retry" instead of a dropped connection while the backend keeps
 * working. Background agents keep the default client and its longer timeouts.
 */
@Configuration
public class InteractiveAiConfig {

	public static final String DEFAULT_SYSTEM = "You are a CV screening expert that answer questions about screening CVs "
		+ "and candidates skills evaluation in different domains";

	static final int ATTEMPTS = 2;
	static final Duration BACKOFF = Duration.ofSeconds(1);

	/** Per attempt: the budget shared by both attempts, less the backoff and a second of margin. */
	static Duration perAttemptTimeout(int budgetSeconds) {
		long seconds = Math.max(5, (budgetSeconds - BACKOFF.toSeconds() - 1) / ATTEMPTS);
		return Duration.ofSeconds(seconds);
	}

	@Bean
	ChatClient interactiveChatClient(ChatModel chatModel, ObjectProvider<OpenAiApi> openAiApi, CloseableHttpClient qorvaHttpClient,
	                                 AiCallMetrics aiCallMetrics,
	                                 @Value("${qorva.ai.interactive.timeout-seconds:50}") int budgetSeconds) {
		var model = chatModel instanceof OpenAiChatModel openAi && openAiApi.getIfAvailable() != null
			? budgeted(openAi, openAiApi.getObject(), qorvaHttpClient, budgetSeconds)
			// Not OpenAI (a test double): nothing to budget, use it as it is.
			: chatModel;
		return ChatClient.builder(model)
			.defaultSystem(DEFAULT_SYSTEM)
			.defaultAdvisors(new AiCallMetricsAdvisor(aiCallMetrics))
			.build();
	}

	private static ChatModel budgeted(OpenAiChatModel openAiChatModel, OpenAiApi openAiApi, CloseableHttpClient qorvaHttpClient,
	                                  int budgetSeconds) {
		var requestFactory = new HttpComponentsClientHttpRequestFactory(qorvaHttpClient);
		requestFactory.setReadTimeout(perAttemptTimeout(budgetSeconds));
		var api = openAiApi.mutate()
			.restClientBuilder(RestClient.builder().requestFactory(requestFactory))
			.build();
		var retry = RetryTemplate.builder()
			.maxAttempts(ATTEMPTS)
			.fixedBackoff(BACKOFF.toMillis())
			.retryOn(TransientAiException.class)
			.retryOn(ResourceAccessException.class)
			.build();
		return openAiChatModel.mutate().openAiApi(api).retryTemplate(retry).build();
	}
}
