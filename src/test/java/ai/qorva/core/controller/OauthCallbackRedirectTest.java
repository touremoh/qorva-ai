package ai.qorva.core.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OauthCallbackRedirectTest {

	private static final String BACK = "https://app.test/app/settings?x=";

	@Test
	void aProviderErrorOrMissingCode_isDenied_withoutRunningTheCompletion() {
		var ran = new boolean[1];
		OauthCallbackRedirect.Completion completion = () -> ran[0] = true;

		assertThat(location(OauthCallbackRedirect.handle("T", "access_denied", "c", "s", BACK, completion))).isEqualTo(BACK + "denied");
		assertThat(location(OauthCallbackRedirect.handle("T", null, null, "s", BACK, completion))).isEqualTo(BACK + "denied");
		assertThat(ran[0]).isFalse();
	}

	@Test
	void aCompletedExchange_isConnected_andAFailure_isFailed_withoutTheReason() {
		assertThat(location(OauthCallbackRedirect.handle("T", null, "c", "s", BACK, () -> { }))).isEqualTo(BACK + "connected");
		assertThat(location(OauthCallbackRedirect.handle("T", null, "c", "s", BACK, () -> {
			throw new IllegalStateException("secret detail");
		}))).isEqualTo(BACK + "failed");
	}

	private static String location(org.springframework.http.ResponseEntity<Void> response) {
		assertThat(response.getStatusCode().value()).isEqualTo(302);
		return response.getHeaders().getLocation().toString();
	}
}
