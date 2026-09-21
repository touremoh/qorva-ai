package ai.qorva.core.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OauthStateSignerTest {

	private final OauthStateSigner signer = new OauthStateSigner("unit-test-key");

	@Test
	void roundTripKeepsFieldOrderAndEmptyFields() {
		var state = signer.sign(List.of("tenant", "user", "MICROSOFT", "123", ""));

		assertThat(signer.verify(state)).containsExactly("tenant", "user", "MICROSOFT", "123", "");
	}

	@Test
	void tamperedSignatureIsRejected() {
		var state = signer.sign(List.of("tenant", "user"));
		var tampered = state.substring(0, state.length() - 2) + "ff";

		assertThatThrownBy(() -> signer.verify(tampered)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void otherKeyIsRejected() {
		var state = signer.sign(List.of("tenant"));

		assertThatThrownBy(() -> new OauthStateSigner("other").verify(state)).isInstanceOf(IllegalStateException.class);
	}
}
