package ai.qorva.core.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretBlobCipherTest {

	@Test
	void roundTripAndSalting() throws GeneralSecurityException {
		var cipher = new SecretBlobCipher("unit-test-key", "test.key");
		var plain = "hello".getBytes(StandardCharsets.UTF_8);

		var first = cipher.encrypt(plain);
		var second = cipher.encrypt(plain);

		assertThat(cipher.decrypt(first)).isEqualTo(plain);
		assertThat(first).isNotEqualTo(second);   // random IV
	}

	@Test
	void otherKeyCannotDecrypt() throws GeneralSecurityException {
		var blob = new SecretBlobCipher("key-a", "test.key").encrypt("x".getBytes(StandardCharsets.UTF_8));
		var other = new SecretBlobCipher("key-b", "test.key");

		assertThatThrownBy(() -> other.decrypt(blob)).isInstanceOf(GeneralSecurityException.class);
	}

	@Test
	void blankKeyIsRefusedAtConstruction() {
		assertThatThrownBy(() -> new SecretBlobCipher(" ", "qorva.mailbox.credentials-key"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("qorva.mailbox.credentials-key");
	}
}
