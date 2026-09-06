package ai.qorva.core.controller;

import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.service.ats.AtsConnectorRegistry;
import ai.qorva.core.service.ats.AtsSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;

/**
 * Unauthenticated ATS webhook endpoints under the /public/** permit-all matcher (the
 * OAuth callback lives in AtsOauthCallbackController). Webhooks answer 200 whatever
 * happens — a prober must not be able to distinguish a valid connection id, token, or
 * signature from an invalid one. Payloads are treated as hints only; real data always
 * comes from a pull.
 */
@Slf4j
@RestController
@RequestMapping("/public/ats")
public class AtsPublicController {

	private final AtsConnectionRepository connectionRepository;
	private final AtsConnectorRegistry registry;
	private final AtsSyncService syncService;

	public AtsPublicController(
		AtsConnectionRepository connectionRepository,
		AtsConnectorRegistry registry,
		AtsSyncService syncService
	) {
		this.connectionRepository = connectionRepository;
		this.registry = registry;
		this.syncService = syncService;
	}

	@PostMapping("/webhooks/{connectionId}")
	public ResponseEntity<Void> webhook(
		@PathVariable String connectionId,
		@RequestParam(name = "token", required = false) String token,
		@RequestHeader HttpHeaders headers,
		@RequestBody(required = false) byte[] body
	) {
		try {
			var connection = connectionRepository.findById(connectionId).orElse(null);
			if (connection == null || body == null) {
				return ResponseEntity.ok().build();
			}
			var provider = AtsProviderEnum.fromValue(connection.getProvider());

			// Signing providers authenticate inside parseWebhook; the rest by the URL token.
			// The flag is on the enum so this can never drift from the URL we handed out.
			if (!provider.signsWebhooks() && !constantTimeEquals(connection.getWebhookSecret(), token)) {
				return ResponseEntity.ok().build();
			}

			var event = registry.get(provider).parseWebhook(headers, body, connection.getWebhookSecret());
			if (event.isPresent() && AtsConnection.STATUS_CONNECTED.equals(connection.getStatus())) {
				syncService.enqueueQuietly(connection, AtsSyncService.TRIGGER_WEBHOOK);
			}
		} catch (Exception e) {
			log.debug("ATS webhook for {} dropped: {}", connectionId, e.getMessage());
		}
		return ResponseEntity.ok().build();
	}

	/** Some providers verify webhook endpoints with a GET/HEAD ping before saving. */
	@GetMapping("/webhooks/{connectionId}")
	public ResponseEntity<Void> webhookPing(@PathVariable String connectionId) {
		return ResponseEntity.ok().build();
	}

	private boolean constantTimeEquals(String expected, String provided) {
		if (expected == null || provided == null) {
			return false;
		}
		return MessageDigest.isEqual(Utf8.encode(expected), Utf8.encode(provided));
	}
}
