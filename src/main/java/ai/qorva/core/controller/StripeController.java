package ai.qorva.core.controller;

import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.service.StripeEventsService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/stripe")
public class StripeController extends AbstractQorvaController<StripeEventLogDTO> {

	@Value("${stripe.webhook.secret}")
	protected String stripeWebhookSecret;

	@Autowired
	protected StripeController(StripeEventsService service) {
		super(service);
	}

	@PostMapping("/webhook")
	public ResponseEntity<String> handleStripeEvent(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
		try {
			Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
			((StripeEventsService)this.service).handleEvent(event);
			return ResponseEntity.ok("Event processed");
		} catch (SignatureVerificationException e) {
			log.error("Stripe signature verification error", e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
		} catch (Exception e) {
			log.error("Stripe webhook processing failed", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook error");
		}
	}

	// Initiate Stripe Checkout Session
	@PutMapping("/init-checkout-session")
	public ResponseEntity<String> initiateCheckoutSession(@RequestBody String payload) {
		return ResponseEntity.ok("Not implemented yet");
	}


	// Close Stripe Checkout Session
	@PutMapping("/close-checkout-session")
	public ResponseEntity<String> closeCheckoutSession(@RequestBody String payload) {
		return ResponseEntity.ok("Not implemented yet");
	}
}
