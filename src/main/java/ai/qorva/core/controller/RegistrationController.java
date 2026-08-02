package ai.qorva.core.controller;

import ai.qorva.core.dto.*;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import ai.qorva.core.service.UserRegistrationService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/registrations")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class RegistrationController {

	private final UserRegistrationService registrationService;
	private final ProductReferenceService productReferenceService;

	@Autowired
	public RegistrationController(UserRegistrationService registrationService, ProductReferenceService productReferenceService) {
		this.registrationService = registrationService;
		this.productReferenceService = productReferenceService;
	}

	@PostMapping(path = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> createUserAccount(
		@RequestBody AccountRegistrationDTO accountRegistrationDTO,
		@RequestHeader("Accept-Language") String languageCode
	) throws QorvaException {
		return BuildApiResponse.from(this.registrationService.createDemoAccount(accountRegistrationDTO, languageCode));
	}

	@PostMapping(path = "/checkout-session", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> renewCheckoutSession(
		@RequestBody CheckoutSessionRequestDTO checkoutSessionRequestDTO
	) throws QorvaException {
		return BuildApiResponse.from(this.registrationService.renewCheckoutSession(checkoutSessionRequestDTO));
	}

	@GetMapping(path = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> getActiveProducts() {
		return BuildApiResponse.from(this.productReferenceService.findAllActive());
	}
}
