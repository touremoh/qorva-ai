package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialsCipherTest {

	private CredentialsCipher cipher;

	@BeforeEach
	void setUp() {
		var properties = new AtsProperties();
		properties.setCredentialsKey("unit-test-key");
		cipher = new CredentialsCipher(properties, new ObjectMapper().findAndRegisterModules());
	}

	@Test
	void roundTripPreservesEveryField() throws QorvaException {
		var credentials = AtsCredentials.builder()
			.apiKey("gh-key")
			.subdomain("acme")
			.companyId("12345")
			.onBehalfOfUserId("777")
			.datacenter("eu")
			.build();

		var decrypted = cipher.decrypt(cipher.encrypt(credentials));

		assertThat(decrypted.getApiKey()).isEqualTo("gh-key");
		assertThat(decrypted.getSubdomain()).isEqualTo("acme");
		assertThat(decrypted.getCompanyId()).isEqualTo("12345");
		assertThat(decrypted.getOnBehalfOfUserId()).isEqualTo("777");
		assertThat(decrypted.getDatacenter()).isEqualTo("eu");
	}

	@Test
	void ciphertextIsNotPlaintextAndIsSalted() throws QorvaException {
		var credentials = AtsCredentials.builder().apiKey("secret-value").build();
		var first = cipher.encrypt(credentials);
		var second = cipher.encrypt(credentials);

		assertThat(first).doesNotContain("secret-value");
		// Random IV: same plaintext must never produce the same blob.
		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void tamperedBlobIsRejected() throws QorvaException {
		var blob = cipher.encrypt(AtsCredentials.builder().apiKey("k").build());
		var tampered = blob.substring(0, blob.length() - 4) + "AAA=";

		assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(QorvaException.class);
	}

	@Test
	void wrongKeyCannotDecrypt() throws QorvaException {
		var blob = cipher.encrypt(AtsCredentials.builder().apiKey("k").build());

		var otherProps = new AtsProperties();
		otherProps.setCredentialsKey("a-different-key");
		var other = new CredentialsCipher(otherProps, new ObjectMapper().findAndRegisterModules());

		assertThatThrownBy(() -> other.decrypt(blob)).isInstanceOf(QorvaException.class);
	}
}
