package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * Owns the lifecycle of automatically registered webhooks: creating them when a connection
 * is made, removing them when it goes away, and putting them back when the callback URL
 * changes underneath.
 *
 * <p>Registration is deliberately best-effort. A provider being down, or an API key missing
 * a webhook permission, must never stop a tenant from connecting — the failure is recorded
 * on the connection and surfaced as a retry in the Integrations tab, and scheduled syncing
 * covers the gap in the meantime.</p>
 *
 * <p>This service also answers the one question the webhook receiver cannot answer alone:
 * which key a given provider actually signs with. Ashby accepts ours, Workable signs with
 * the account API token, and Lever signs with a token Lever generates. Getting that wrong
 * silently drops every delivery, so it lives in exactly one place.</p>
 */
@Slf4j
@Service
public class AtsWebhookService {

	private final AtsConnectionRepository connectionRepository;
	private final AtsConnectorRegistry registry;
	private final CredentialsCipher cipher;
	private final AtsProperties properties;

	public AtsWebhookService(AtsConnectionRepository connectionRepository, AtsConnectorRegistry registry,
		CredentialsCipher cipher, AtsProperties properties) {
		this.connectionRepository = connectionRepository;
		this.registry = registry;
		this.cipher = cipher;
		this.properties = properties;
	}

	/**
	 * The inbound webhook URL for a connection.
	 *
	 * <p>Signing providers verify the body signature inside the connector; the rest
	 * authenticate by the token in the URL itself, which is why only they get it appended.
	 * The flag comes from the enum so the URL handed out and the check on delivery can never
	 * disagree.</p>
	 */
	public String webhookUrl(AtsConnection connection) {
		var base = properties.getPublicBaseUrl() + "/public/ats/webhooks/" + connection.getId();
		var provider = AtsProviderEnum.fromValue(connection.getProvider());
		return provider.signsWebhooks() ? base : base + "?token=" + connection.getWebhookSecret();
	}

	/**
	 * The HMAC key {@code parseWebhook} must verify against for this provider. Only the
	 * providers that insist on choosing the key differ from the connection's own secret.
	 */
	public String signingSecret(AtsConnection connection, AtsCredentials credentials) {
		return switch (AtsProviderEnum.fromValue(connection.getProvider())) {
			case WORKABLE -> credentials.getApiKey();
			case LEVER -> StringUtils.hasText(credentials.getWebhookSigningSecret())
				? credentials.getWebhookSigningSecret()
				: connection.getWebhookSecret();
			default -> connection.getWebhookSecret();
		};
	}

	public boolean supportsRegistration(AtsProviderEnum provider) {
		return registry.get(provider).supportsWebhookRegistration();
	}

	/**
	 * Register (or re-register) this connection's webhooks, saving the outcome either way.
	 * Never throws: the caller is a connect or a scheduled reconcile, and neither should fail
	 * because a provider's webhook API did.
	 */
	public void register(AtsConnection connection) {
		var provider = AtsProviderEnum.fromValue(connection.getProvider());
		var connector = registry.get(provider);
		if (!connector.supportsWebhookRegistration()) {
			return;
		}
		try {
			var credentials = cipher.decrypt(connection.getEncryptedCredentials());

			// Drop what is already out there first, so a re-register cannot leave the tenant
			// with two subscriptions delivering the same event twice.
			unregisterQuietly(connection, connector, credentials);

			var url = webhookUrl(connection);
			var registration = connector.registerWebhooks(credentials, url, connection.getWebhookSecret());

			// A provider-chosen signing key, or a discovered account id, are both credential
			// material and go back into the encrypted blob rather than a plain field.
			boolean credentialsChanged = false;
			if (StringUtils.hasText(registration.signingSecret())) {
				credentials.setWebhookSigningSecret(registration.signingSecret());
				credentialsChanged = true;
			}
			if (StringUtils.hasText(credentials.getAccountId())) {
				credentialsChanged = true;
			}
			if (credentialsChanged) {
				connection.setEncryptedCredentials(cipher.encrypt(credentials));
			}

			connection.setWebhookState(AtsConnection.WebhookState.builder()
				.externalIds(registration.externalIds())
				.registeredUrl(url)
				.registeredAt(Instant.now())
				.build());
			connectionRepository.save(connection);
			log.info("Registered {} webhooks for ATS connection {}",
				registration.externalIds().size(), connection.getId());
		} catch (Exception e) {
			// QorvaException carries its key in a Lombok-shadowed field, so getMessage() can be
			// null; the card must still show the tenant something to act on.
			var reason = StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
			log.warn("Webhook registration failed for ATS connection {}: {}", connection.getId(), reason);
			var state = connection.getWebhookState() != null
				? connection.getWebhookState()
				: AtsConnection.WebhookState.builder().build();
			state.setLastError(reason);
			connection.setWebhookState(state);
			connectionRepository.save(connection);
		}
	}

	/** Remove this connection's webhooks from the provider. Called before a connection is deleted. */
	public void unregister(AtsConnection connection) {
		var provider = AtsProviderEnum.fromValue(connection.getProvider());
		var connector = registry.get(provider);
		if (!connector.supportsWebhookRegistration()) {
			return;
		}
		try {
			unregisterQuietly(connection, connector, cipher.decrypt(connection.getEncryptedCredentials()));
		} catch (QorvaException e) {
			log.warn("Could not read credentials to remove webhooks for {}: {}", connection.getId(), e.getMessage());
		}
	}

	/**
	 * Re-register when the registration is missing, previously failed, or points at a URL we
	 * no longer serve — the last case is what happens after ATS_PUBLIC_BASE_URL changes, and
	 * without it the connection would go quietly deaf.
	 */
	public void reconcile(AtsConnection connection) {
		if (!supportsRegistration(AtsProviderEnum.fromValue(connection.getProvider()))) {
			return;
		}
		var state = connection.getWebhookState();
		boolean stale = state == null
			|| state.getRegisteredAt() == null
			|| StringUtils.hasText(state.getLastError())
			|| !webhookUrl(connection).equals(state.getRegisteredUrl());
		if (stale) {
			register(connection);
		}
	}

	private void unregisterQuietly(AtsConnection connection, AtsConnector connector, AtsCredentials credentials) {
		var state = connection.getWebhookState();
		var ids = state != null ? state.getExternalIds() : null;
		if (ids != null && !ids.isEmpty()) {
			connector.unregisterWebhooks(credentials, List.copyOf(ids));
		}
	}
}
