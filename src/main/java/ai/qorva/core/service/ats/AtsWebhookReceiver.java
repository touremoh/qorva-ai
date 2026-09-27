package ai.qorva.core.service.ats;

import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.security.TenantScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;

/**
 * An ATS webhook delivery. Public and unauthenticated: the connection id in the URL names the
 * connection, and the delivery is authenticated by the provider's signature (signing providers) or
 * the URL token (the others). Once the connection is known, the work runs in its tenant's scope.
 * Never throws: a provider only needs a 200, and nothing about a failure is told to the caller.
 */
@Slf4j
@Service
public class AtsWebhookReceiver {

	private final AtsConnectionRepository connectionRepository;
	private final AtsConnectorRegistry registry;
	private final AtsSyncService syncService;
	private final AtsConnectionService connectionService;
	private final AtsWebhookService webhookService;

	public AtsWebhookReceiver(AtsConnectionRepository connectionRepository, AtsConnectorRegistry registry,
	                          AtsSyncService syncService, AtsConnectionService connectionService,
	                          AtsWebhookService webhookService) {
		this.connectionRepository = connectionRepository;
		this.registry = registry;
		this.syncService = syncService;
		this.connectionService = connectionService;
		this.webhookService = webhookService;
	}

	public void receive(String connectionId, String token, HttpHeaders headers, byte[] body) {
		try {
			var connection = connectionRepository.findById(connectionId).orElse(null);
			if (connection == null || body == null) {
				return;
			}
			TenantScope.runAs(connection.getTenantId(), () -> handle(connection, token, headers, body));
		} catch (Exception e) {
			log.debug("ATS webhook for {} dropped: {}", connectionId, e.getMessage());
		}
	}

	private void handle(AtsConnection connection, String token, HttpHeaders headers, byte[] body) throws Exception {
		var provider = AtsProviderEnum.fromValue(connection.getProvider());

		// Signing providers authenticate inside parseWebhook; the rest by the URL token.
		// The flag is on the enum so this can never drift from the URL we handed out.
		if (!provider.signsWebhooks() && !constantTimeEquals(connection.getWebhookSecret(), token)) {
			return;
		}

		// Which key proves authenticity differs per provider — Workable signs with the
		// account token, Lever with its own signing token — so ask rather than assume.
		var signingSecret = webhookService.signingSecret(connection, connectionService.decryptCredentials(connection));
		var event = registry.get(provider).parseWebhook(headers, body, signingSecret);
		if (event.isPresent() && AtsConnection.STATUS_CONNECTED.equals(connection.getStatus())) {
			syncService.enqueueQuietly(connection, AtsSyncService.TRIGGER_WEBHOOK);
		}
	}

	private static boolean constantTimeEquals(String expected, String provided) {
		if (expected == null || provided == null) {
			return false;
		}
		return MessageDigest.isEqual(Utf8.encode(expected), Utf8.encode(provided));
	}
}
