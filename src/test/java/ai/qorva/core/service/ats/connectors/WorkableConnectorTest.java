package ai.qorva.core.service.ats.connectors;

import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.service.ats.AtsCredentials;
import ai.qorva.core.service.ats.AtsHttpClient;
import ai.qorva.core.service.ats.AtsWebhookVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkableConnectorTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock private AtsHttpClient http;

	private WorkableConnector connector;

	@BeforeEach
	void setUp() {
		connector = new WorkableConnector(http, objectMapper);
	}

	private static AtsCredentials credentials() {
		return AtsCredentials.builder().apiKey("wk-token").subdomain("acme").build();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object captured) {
		return (Map<String, Object>) captured;
	}

	/**
	 * The subscriptions API wants the numeric account id, which is not the subdomain. It is
	 * looked up once and cached on the credentials so a re-register does not pay for it again.
	 */
	@Test
	void registrationLooksUpTheAccountIdOnceAndCachesIt() throws Exception {
		var credentials = credentials();
		when(http.getJson(eq(AtsProviderEnum.WORKABLE), contains("/accounts"), anyMap()))
			.thenReturn(objectMapper.readTree("{\"accounts\": [{\"id\": \"acct_9\"}]}"));
		when(http.postJsonIgnoringConflict(eq(AtsProviderEnum.WORKABLE), contains("/subscriptions"), anyMap(), any()))
			.thenReturn(Optional.of(objectMapper.readTree("{\"id\": \"sub_1\"}")));

		var registration = connector.registerWebhooks(credentials, "https://api.qorva.test/hook", "unused");

		var payload = ArgumentCaptor.forClass(Object.class);
		verify(http, atLeastOnce()).postJsonIgnoringConflict(
			eq(AtsProviderEnum.WORKABLE), contains("/subscriptions"), anyMap(), payload.capture());
		var sent = asMap(payload.getValue());
		assertThat(sent).containsEntry("target", "https://api.qorva.test/hook");
		// Empty shortcode and stage mean every job and every stage.
		assertThat(asMap(sent.get("args")))
			.containsEntry("account_id", "acct_9")
			.containsEntry("job_shortcode", "")
			.containsEntry("stage_slug", "");
		assertThat(registration.externalIds()).isNotEmpty();
		assertThat(credentials.getAccountId()).isEqualTo("acct_9");

		// Second pass: the cached id means no second /accounts call.
		connector.registerWebhooks(credentials, "https://api.qorva.test/hook", "unused");
		verify(http).getJson(eq(AtsProviderEnum.WORKABLE), contains("/accounts"), anyMap());
	}

	/**
	 * Workable answers 409 when that URL is already subscribed for the event. That is the end
	 * state we wanted, so registration must carry on rather than fail the whole connect.
	 */
	@Test
	void anAlreadyRegisteredSubscriptionIsNotAnError() throws Exception {
		var credentials = credentials();
		credentials.setAccountId("acct_9");
		when(http.postJsonIgnoringConflict(eq(AtsProviderEnum.WORKABLE), contains("/subscriptions"), anyMap(), any()))
			.thenReturn(Optional.empty());

		var registration = connector.registerWebhooks(credentials, "https://api.qorva.test/hook", "unused");

		assertThat(registration.externalIds()).isEmpty();
		assertThat(registration.signingSecret()).isNull();
	}

	@Test
	void unregisterDeletesEveryStoredSubscription() throws Exception {
		connector.unregisterWebhooks(credentials(), List.of("sub_1", "sub_2"));

		verify(http).delete(eq(AtsProviderEnum.WORKABLE), contains("/subscriptions/sub_1"), anyMap());
		verify(http).delete(eq(AtsProviderEnum.WORKABLE), contains("/subscriptions/sub_2"), anyMap());
	}

	/**
	 * Workable signs with the account token, not with a secret Qorva picks. This is the bug
	 * that made every Workable delivery fail verification before, so it is pinned.
	 */
	@Test
	void webhooksVerifyAgainstTheAccountTokenNotAGeneratedSecret() {
		var body = "{\"event_type\":\"candidate_created\",\"data\":{\"id\":\"cand_1\"}}"
			.getBytes(StandardCharsets.UTF_8);
		var headers = new HttpHeaders();
		headers.add("X-Workable-Signature", AtsWebhookVerifier.hmacHex(body, "wk-token"));

		assertThat(connector.parseWebhook(headers, body, "wk-token"))
			.isPresent()
			.hasValueSatisfying(event -> assertThat(event.externalId()).isEqualTo("cand_1"));

		// The connection's own random secret must not verify anything here.
		assertThat(connector.parseWebhook(headers, body, "qorva-secret")).isEmpty();
	}
}
