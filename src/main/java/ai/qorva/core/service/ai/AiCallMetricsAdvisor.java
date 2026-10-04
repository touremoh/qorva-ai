package ai.qorva.core.service.ai;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

/** Records every ChatClient call in {@link AiCallMetrics}; the agent comes from the {@link AiCallMetrics#AGENT} param. */
public class AiCallMetricsAdvisor implements CallAdvisor {

	private final AiCallMetrics metrics;

	public AiCallMetricsAdvisor(AiCallMetrics metrics) {
		this.metrics = metrics;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		var agent = request.context().get(AiCallMetrics.AGENT);
		var options = request.prompt().getOptions();
		var model = options != null ? options.getModel() : null;
		return metrics.record(agent != null ? agent.toString() : null, model, () -> chain.nextCall(request));
	}

	@Override
	public String getName() {
		return "qorvaAiCallMetrics";
	}

	@Override
	public int getOrder() {
		// Outermost: the time covers every other advisor and the model's retries.
		return Ordered.HIGHEST_PRECEDENCE;
	}
}
