package ai.qorva.core.service.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundUrlGuardTest {

	@ParameterizedTest
	@ValueSource(strings = {
		"http://api.recruitee.com/c/1",            // not https
		"https://169.254.169.254/latest/meta-data", // cloud metadata
		"https://127.0.0.1/admin",
		"https://localhost:8080/actuator",
		"https://10.0.0.5/internal",
		"https://192.168.1.10/",
		"https://172.16.0.1/",
		"https://[::1]/",
		"https://[fd00::1]/",
		"file:///etc/passwd"
	})
	void internalOrNonHttpsTargetsAreRefused(String url) {
		assertThat(OutboundUrlGuard.refusal(URI.create(url))).isNotNull();
	}

	@Test
	void aPublicProviderHostIsAllowed() {
		assertThat(OutboundUrlGuard.refusal(URI.create("https://api.recruitee.com/c/1/candidates"))).isNull();
	}

	@Test
	void theClientRefusesBeforeSendingAnything() {
		var client = new AtsHttpClient(RestClient.builder(), new ObjectMapper());
		assertThatThrownBy(() -> client.getBytes(AtsProviderEnum.RECRUITEE, "https://169.254.169.254/latest/meta-data", Map.of()))
			.isInstanceOf(QorvaException.class);
	}
}
