package ai.qorva.core.controller;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.PortalSession;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.requests.StripeEventRequestMapper;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.service.StripeEventsService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/stripe")
public class StripeController extends AbstractQorvaController<StripeEventLogDTO> {

	@Value("${stripe.webhook.secret}")
	protected String stripeWebhookSecret;

	@Autowired
	public StripeController(StripeEventsService service, StripeEventRequestMapper requestMapper, QorvaUserDetailsService userService, JwtConfig jwtConfig) {
		super(service, requestMapper, userService, jwtConfig);
	}

	@PostMapping("/webhook")
	public ResponseEntity<String> handleStripeEvent(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
		try {
			Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
			((StripeEventsService)this.service).handleEvent(event);
			return ResponseEntity.ok("success");
		} catch (SignatureVerificationException e) {
			log.error("Stripe signature verification error", e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
		} catch (Exception e) {
			log.error("Stripe webhook processing failed", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook error");
		}
	}

	@PostMapping("/portal-session")
	public ResponseEntity<PortalSession> createPortalSession(@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		return ResponseEntity.ok(((StripeEventsService)this.service).buildStripePortalSessionUrl(userDetails));
	}
}
