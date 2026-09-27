package ai.qorva.core.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/**
 * The shape every OAuth provider callback shares: no code or state (or a provider error) means the
 * user declined; otherwise the completion runs; either way the browser goes back to the app with
 * {@code denied}, {@code connected} or {@code failed} — never with the reason, which only the log sees.
 */
@Slf4j
final class OauthCallbackRedirect {

	@FunctionalInterface
	interface Completion {
		void complete() throws Exception;
	}

	private OauthCallbackRedirect() {
	}

	static ResponseEntity<Void> handle(String label, String error, String code, String state,
	                                   String redirectPrefix, Completion completion) {
		String result;
		if (error != null || code == null || state == null) {
			result = "denied";
		} else {
			try {
				completion.complete();
				result = "connected";
			} catch (Exception e) {
				log.warn("{} OAuth callback failed: {}", label, e.getMessage());
				result = "failed";
			}
		}
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectPrefix + result)).build();
	}
}
