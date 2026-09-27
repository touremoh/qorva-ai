package ai.qorva.core.controller;

import ai.qorva.core.service.ats.AtsWebhookReceiver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * Unauthenticated ATS webhook endpoints under the /public/** permit-all matcher (the
 * OAuth callback lives in AtsOauthCallbackController). Webhooks answer 200 whatever
 * happens — a prober must not be able to distinguish a valid connection id, token, or
 * signature from an invalid one. Payloads are treated as hints only; real data always
 * comes from a pull.
 */
@RestController
@RequestMapping("/public/ats")
public class AtsPublicController {

	private final AtsWebhookReceiver webhookReceiver;

	public AtsPublicController(AtsWebhookReceiver webhookReceiver) {
		this.webhookReceiver = webhookReceiver;
	}

	/** Always 200: providers only need an acknowledgement, and a failure must not be explained to the caller. */
	@PostMapping("/webhooks/{connectionId}")
	public ResponseEntity<Void> webhook(
		@PathVariable String connectionId,
		@RequestParam(name = "token", required = false) String token,
		@RequestHeader HttpHeaders headers,
		@RequestBody(required = false) byte[] body
	) {
		webhookReceiver.receive(connectionId, token, headers, body);
		return ResponseEntity.ok().build();
	}

	/** Some providers verify webhook endpoints with a GET/HEAD ping before saving. */
	@GetMapping("/webhooks/{connectionId}")
	public ResponseEntity<Void> webhookPing(@PathVariable String connectionId) {
		return ResponseEntity.ok().build();
	}
}
