package ai.qorva.core.service.ai;

import ai.qorva.core.security.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Times every model call by agent and outcome ({@code qorva.ai.call}) and writes one key=value log line per call,
 * which CloudWatch Logs metric filters turn into the failure rate and latency (no prompt content, ever). Retries
 * are inside the call, so a call that succeeded on its second attempt is one slow "ok". A failure is rethrown as
 * {@link AiCallFailedException}, the original kept as its cause.
 */
@Slf4j
@Component
public class AiCallMetrics {

	/** ChatClient advisor parameter naming the agent making the call. */
	public static final String AGENT = "qorva.ai.agent";
	public static final String METRIC = "qorva.ai.call";

	public static final String OK = "ok";
	public static final String TIMEOUT = "timeout";
	public static final String REFUSED = "refused";
	public static final String ERROR = "error";

	private final MeterRegistry registry;

	public AiCallMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public <T> T record(String agent, String model, Supplier<T> call) {
		long started = System.nanoTime();
		try {
			T result = call.get();
			done(agent, model, OK, started, null);
			return result;
		} catch (AiCallFailedException e) {
			throw e;
		} catch (RuntimeException e) {
			var outcome = outcome(e);
			done(agent, model, outcome, started, e);
			throw new AiCallFailedException(agent != null ? agent : "unknown", outcome, e);
		}
	}

	private void done(String agent, String model, String outcome, long startedNanos, Throwable error) {
		long nanos = System.nanoTime() - startedNanos;
		var name = agent != null ? agent : "unknown";
		Timer.builder(METRIC).tag("agent", name).tag("outcome", outcome).register(registry).record(nanos, TimeUnit.NANOSECONDS);
		long latencyMs = TimeUnit.NANOSECONDS.toMillis(nanos);
		var tenant = TenantContextHolder.getTenantId();
		if (error == null) {
			log.info("ai_call agent={} outcome={} model={} latencyMs={} tenant={}", name, outcome, model, latencyMs, tenant);
		} else {
			log.warn("ai_call agent={} outcome={} model={} latencyMs={} tenant={} error={}", name, outcome, model, latencyMs,
				tenant, error.getClass().getSimpleName());
		}
	}

	static String outcome(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof SocketTimeoutException || t instanceof java.net.http.HttpTimeoutException
				|| t instanceof org.apache.hc.core5.http.ConnectionRequestTimeoutException
				|| (t.getMessage() != null && t.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timed out"))) {
				return TIMEOUT;
			}
		}
		return e instanceof NonTransientAiException ? REFUSED : ERROR;
	}
}
