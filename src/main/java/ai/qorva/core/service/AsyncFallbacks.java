package ai.qorva.core.service;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Degrades one slow or failing report to a fallback value instead of failing the whole
 * aggregate response. The returned future must be the one that is joined — the fallback
 * stage lives only on it, not on the future passed in.
 */
@Slf4j
final class AsyncFallbacks {

	private AsyncFallbacks() {}

	static <T> CompletableFuture<T> withFallback(CompletableFuture<T> future, long timeoutSeconds, T fallback, String name) {
		return future
			.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
			.exceptionally(ex -> {
				log.warn("Dashboard report {} failed or timed out, using fallback", name, ex);
				return fallback;
			});
	}
}
