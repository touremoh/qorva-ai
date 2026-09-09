package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ats.AtsModels.WebhookRegistration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtsWebhookServiceTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";

	@Mock private AtsConnectionRepository connectionRepository;
	@Mock private AtsConnectorRegistry registry;
	@Mock private AtsConnector connector;

	private AtsWebhookService service;
	private CredentialsCipher cipher;
	private AtsProperties properties;

	@BeforeEach
	void setUp() {
		properties = new AtsProperties();
		properties.setCredentialsKey("unit-test-key");
		properties.setPublicBaseUrl("https://api.qorva.test");
		cipher = new CredentialsCipher(properties, new ObjectMapper().findAndRegisterModules());
		service = new AtsWebhookService(connectionRepository, registry, cipher, properties);
	}

	private AtsConnection connection(String provider, AtsCredentials credentials) throws QorvaException {
		return AtsConnection.builder()
			.id("c1").tenantId(TENANT).provider(provider)
			.status(AtsConnection.STATUS_CONNECTED)
			.encryptedCredentials(cipher.encrypt(credentials))
			.webhookSecret("qorva-secret")
			.build();
	}

	// ------------------------------------------------------------------ callback URL

	/**
	 * The token in the URL is the only thing authenticating an unsigned provider's delivery,
	 * so it must be there — and must not be there for signed ones, where it would leak the
	 * HMAC key into logs and referrers for no benefit.
	 */
	@Test
	void onlyUnsignedProvidersCarryTheTokenInTheUrl() throws QorvaException {
		var manatal = connection("manatal", AtsCredentials.builder().apiKey("k").build());
		assertThat(service.webhookUrl(manatal))
			.isEqualTo("https://api.qorva.test/public/ats/webhooks/c1?token=qorva-secret");

		var ashby = connection("ashby", AtsCredentials.builder().apiKey("k").build());
		assertThat(service.webhookUrl(ashby)).isEqualTo("https://api.qorva.test/public/ats/webhooks/c1");
	}

	// ------------------------------------------------------------------ signing key

	/**
	 * The three providers that do not verify against the connection's own secret. Getting any
	 * of these wrong drops every delivery silently, which is why they are pinned here.
	 */
	@Test
	void signingKeyIsWhicheverKeyTheProviderActuallySignsWith() throws QorvaException {
		var workable = connection("workable", AtsCredentials.builder().apiKey("wk-token").build());
		assertThat(service.signingSecret(workable, cipher.decrypt(workable.getEncryptedCredentials())))
			.isEqualTo("wk-token");

		var leverCreds = AtsCredentials.builder().apiKey("lv-key").webhookSigningSecret("lv-signing").build();
		var lever = connection("lever", leverCreds);
		assertThat(service.signingSecret(lever, cipher.decrypt(lever.getEncryptedCredentials())))
			.isEqualTo("lv-signing");

		var ashby = connection("ashby", AtsCredentials.builder().apiKey("as-key").build());
		assertThat(service.signingSecret(ashby, cipher.decrypt(ashby.getEncryptedCredentials())))
			.isEqualTo("qorva-secret");
	}

	/** Lever without a signing token yet: fall back rather than hand parseWebhook a null key. */
	@Test
	void leverWithoutASigningTokenFallsBackToTheConnectionSecret() throws QorvaException {
		var lever = connection("lever", AtsCredentials.builder().apiKey("lv-key").build());
		assertThat(service.signingSecret(lever, cipher.decrypt(lever.getEncryptedCredentials())))
			.isEqualTo("qorva-secret");
	}

	// ------------------------------------------------------------------ registration

	@Test
	void registrationStoresTheSubscriptionIdsAndTheUrlTheyPointAt() throws QorvaException {
		var connection = connection("ashby", AtsCredentials.builder().apiKey("k").build());
		when(registry.get(AtsProviderEnum.ASHBY)).thenReturn(connector);
		when(connector.supportsWebhookRegistration()).thenReturn(true);
		when(connector.registerWebhooks(any(), anyString(), eq("qorva-secret")))
			.thenReturn(WebhookRegistration.of(List.of("w1", "w2")));

		service.register(connection);

		var state = connection.getWebhookState();
		assertThat(state.getExternalIds()).containsExactly("w1", "w2");
		assertThat(state.getRegisteredUrl()).isEqualTo("https://api.qorva.test/public/ats/webhooks/c1");
		assertThat(state.getRegisteredAt()).isNotNull();
		assertThat(state.getLastError()).isNull();
		verify(connectionRepository).save(connection);
	}

	/**
	 * A provider-chosen signing key must land in the encrypted blob, never in a plain field,
	 * and must survive as something the receiver can read back.
	 */
	@Test
	void aProviderChosenSigningKeyIsStoredEncrypted() throws QorvaException {
		var connection = connection("lever", AtsCredentials.builder().apiKey("lv-key").build());
		when(registry.get(AtsProviderEnum.LEVER)).thenReturn(connector);
		when(connector.supportsWebhookRegistration()).thenReturn(true);
		when(connector.registerWebhooks(any(), anyString(), anyString()))
			.thenReturn(new WebhookRegistration(List.of("w1"), "lever-signing-token"));

		service.register(connection);

		assertThat(connection.getEncryptedCredentials()).doesNotContain("lever-signing-token");
		var stored = cipher.decrypt(connection.getEncryptedCredentials());
		assertThat(stored.getWebhookSigningSecret()).isEqualTo("lever-signing-token");
		assertThat(service.signingSecret(connection, stored)).isEqualTo("lever-signing-token");
	}

	/**
	 * Connecting must not fail because a provider's webhook API did — the tenant still gets a
	 * working connection on scheduled syncs, with the reason recorded for the retry button.
	 */
	@Test
	void aFailedRegistrationIsRecordedRatherThanThrown() throws QorvaException {
		var connection = connection("ashby", AtsCredentials.builder().apiKey("k").build());
		when(registry.get(AtsProviderEnum.ASHBY)).thenReturn(connector);
		when(connector.supportsWebhookRegistration()).thenReturn(true);
		when(connector.registerWebhooks(any(), anyString(), anyString()))
			.thenThrow(new QorvaException("error.ats.api_error"));

		service.register(connection);

		assertThat(connection.getWebhookState().getLastError()).isNotNull();
		assertThat(connection.getWebhookState().getRegisteredAt()).isNull();
		verify(connectionRepository).save(connection);
	}

	/** Re-registering drops the old subscriptions first, or every event arrives twice. */
	@Test
	void reRegisteringRemovesThePreviousSubscriptionsFirst() throws QorvaException {
		var connection = connection("ashby", AtsCredentials.builder().apiKey("k").build());
		connection.setWebhookState(AtsConnection.WebhookState.builder()
			.externalIds(List.of("old1")).registeredUrl("https://old.example/hook")
			.registeredAt(Instant.now()).build());
		when(registry.get(AtsProviderEnum.ASHBY)).thenReturn(connector);
		when(connector.supportsWebhookRegistration()).thenReturn(true);
		when(connector.registerWebhooks(any(), anyString(), anyString()))
			.thenReturn(WebhookRegistration.of(List.of("new1")));

		service.register(connection);

		verify(connector).unregisterWebhooks(any(), eq(List.of("old1")));
		assertThat(connection.getWebhookState().getExternalIds()).containsExactly("new1");
	}

	// ------------------------------------------------------------------ reconcile

	/**
	 * The case that keeps a deployment honest: ATS_PUBLIC_BASE_URL moved, so the registered
	 * subscriptions point somewhere Qorva no longer answers.
	 */
	@Test
	void reconcileReRegistersWhenTheCallbackUrlMoved() throws QorvaException {
		var connection = connection("ashby", AtsCredentials.builder().apiKey("k").build());
		connection.setWebhookState(AtsConnection.WebhookState.builder()
			.externalIds(List.of("w1")).registeredUrl("https://old.qorva.test/public/ats/webhooks/c1")
			.registeredAt(Instant.now()).build());
		when(registry.get(AtsProviderEnum.ASHBY)).thenReturn(connector);
		when(connector.supportsWebhookRegistration()).thenReturn(true);
		when(connector.registerWebhooks(any(), anyString(), anyString()))
			.thenReturn(WebhookRegistration.of(List.of("w2")));

		service.reconcile(connection);

		assertThat(connection.getWebhookState().getRegisteredUrl())
			.isEqualTo("https://api.qorva.test/public/ats/webhooks/c1");
	}

	@Test
	void reconcileLeavesAHealthyRegistrationAlone() throws QorvaException {
		var connection = connection("ashby", AtsCredentials.builder().apiKey("k").build());
		connection.setWebhookState(AtsConnection.WebhookState.builder()
			.externalIds(List.of("w1"))
			.registeredUrl("https://api.qorva.test/public/ats/webhooks/c1")
			.registeredAt(Instant.now()).build());
		when(registry.get(AtsProviderEnum.ASHBY)).thenReturn(connector);
		when(connector.supportsWebhookRegistration()).thenReturn(true);

		service.reconcile(connection);

		verify(connector, never()).registerWebhooks(any(), anyString(), anyString());
		verify(connectionRepository, never()).save(any());
	}

	/** Providers with no registration API must never be touched by any of this. */
	@Test
	void providersWithoutRegistrationAreLeftToTheirManualSetup() throws QorvaException {
		var connection = connection("greenhouse", AtsCredentials.builder().clientId("i").clientSecret("s").build());
		when(registry.get(AtsProviderEnum.GREENHOUSE)).thenReturn(connector);

		service.register(connection);
		service.unregister(connection);

		verify(connector, never()).registerWebhooks(any(), anyString(), anyString());
		verify(connectionRepository, never()).save(any());
	}
}
