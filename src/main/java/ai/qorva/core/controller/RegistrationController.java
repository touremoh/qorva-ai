package ai.qorva.core.controller;

import ai.qorva.core.dto.AccountRegistrationDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.UserRegistrationService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/register")
public class RegistrationController {

	private final UserRegistrationService registrationService;

	@Autowired
	public RegistrationController(UserRegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping
	public ResponseEntity<QorvaRequestResponse> register(
		@RequestBody AccountRegistrationDTO accountRegistrationDTO,
		@RequestHeader("Accept-Language") String languageCode) throws QorvaException {
		accountRegistrationDTO.setLanguageCode(languageCode);
		return BuildApiResponse.from(this.registrationService.createAccount(accountRegistrationDTO));
	}
}
