package ai.qorva.core.controller;

import ai.qorva.core.dto.*;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.StripeEventsService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
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
	protected StripeEventsService stripeEventsService;

	@Autowired
	protected StripeController(StripeEventsService service) {
		super(service);
		this.stripeEventsService = service;
	}

	@PostMapping("/webhook")
	public ResponseEntity<String> handleStripeEvent(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
		try {
			Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
			this.stripeEventsService.handleEvent(event);
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
	@PostMapping("/customer/create")
	public ResponseEntity<StripeCustomerDTO> createCustomer(@RequestBody StripeCustomerDTO customerDTO) throws QorvaException {
		return ResponseEntity.ok(this.stripeEventsService.createCustomer(customerDTO));
	}

	// Initiate Stripe Checkout Session
	@PostMapping("/checkout/create")
	public ResponseEntity<CheckoutResponse> createCheckout(@RequestBody CheckoutRequest request) throws QorvaException {
		return ResponseEntity.ok(this.stripeEventsService.createCheckoutSession(request));
	}


	// Close Stripe Checkout Session
	@PutMapping("/checkout/finalize")
	public ResponseEntity<String> closeCheckout(@RequestBody StripeEventLogDTO event) {
		return ResponseEntity.ok("Not implemented yet");
	}
}
