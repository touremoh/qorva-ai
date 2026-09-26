package ai.qorva.core.service.ats;

import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.security.TenantScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtsWebhookReceiverTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";
	private static final byte[] BODY = "{}".getBytes(StandardCharsets.UTF_8);

	@Mock private AtsConnectionRepository connectionRepository;
	@Mock private AtsConnectorRegistry registry;
	@Mock private AtsSyncService syncService;
	@Mock private AtsConnectionService connectionService;
	@Mock private AtsWebhookService webhookService;
	@Mock private AtsConnector connector;

	private AtsWebhookReceiver receiver;
	private AtsConnection connection;

	@BeforeEach
	void setUp() {
		receiver = new AtsWebhookReceiver(connectionRepository, registry, syncService, connectionService, webhookService);
		connection = new AtsConnection();
		connection.setId("c1");
		connection.setTenantId(TENANT);
		connection.setProvider("recruitee");   // unsigned: authenticated by the URL token
		connection.setWebhookSecret("url-token");
		connection.setStatus(AtsConnection.STATUS_CONNECTED);
		when(connectionRepository.findById("c1")).thenReturn(Optional.of(connection));
	}

	@Test
	void aValidDelivery_enqueuesASyncInTheConnectionsTenant() throws Exception {
		when(registry.get(any())).thenReturn(connector);
		when(connector.parseWebhook(any(), any(), any())).thenReturn(Optional.of(new AtsModels.AtsWebhookEvent("candidate", "1")));
		var tenantDuringEnqueue = new AtomicReference<String>();
		doAnswer(call -> {
			tenantDuringEnqueue.set(TenantScope.current());
			return null;
		}).when(syncService).enqueueQuietly(connection, AtsSyncService.TRIGGER_WEBHOOK);

		receiver.receive("c1", "url-token", new HttpHeaders(), BODY);

		assertThat(tenantDuringEnqueue.get()).isEqualTo(TENANT);
		assertThat(TenantScope.current()).isNull();
	}

	@Test
	void aWrongUrlToken_isIgnoredSilently() {
		receiver.receive("c1", "guess", new HttpHeaders(), BODY);

		verify(syncService, never()).enqueueQuietly(any(), anyString());
	}
}
