package ai.qorva.core.config;

import ai.qorva.core.service.ai.AiCallMetrics;
import ai.qorva.core.service.ai.AiCallMetricsAdvisor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The model client for Qorva Help. Same request budget as the other interactive calls, but with no default
 * system prompt: the help assistant's only instructions are its own prompt, and it never carries tools.
 */
@Configuration
public class HelpAiConfig {

	@Bean
	ChatClient helpChatClient(ChatModel chatModel, ObjectProvider<OpenAiApi> openAiApi, CloseableHttpClient qorvaHttpClient,
	                          AiCallMetrics aiCallMetrics,
	                          @Value("${qorva.ai.interactive.timeout-seconds:50}") int budgetSeconds) {
		var model = chatModel instanceof OpenAiChatModel openAi && openAiApi.getIfAvailable() != null
			? InteractiveAiConfig.budgeted(openAi, openAiApi.getObject(), qorvaHttpClient, budgetSeconds)
			// Not OpenAI (a test double): nothing to budget, use it as it is.
			: chatModel;
		return ChatClient.builder(model)
			.defaultAdvisors(new AiCallMetricsAdvisor(aiCallMetrics))
			.build();
	}
}
