package ai.qorva.core.controller;

import ai.qorva.core.dto.*;
import ai.qorva.core.exception.QorvaException;
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

	@Autowired
	public RegistrationController(UserRegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping(path = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> createUserAccount(
		@RequestBody AccountRegistrationDTO accountRegistrationDTO,
		@RequestHeader("Accept-Language") String languageCode
	) throws QorvaException {
		return BuildApiResponse.from(this.registrationService.createAccount(accountRegistrationDTO, languageCode));
	}
}
