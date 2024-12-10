package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.UserService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/register")
public class RegistrationController {

	private final UserService userService;

	public RegistrationController(UserService userService) {
		this.userService = userService;
	}

	@PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> register(
		@RequestBody UserDTO userDTO,
		@RequestHeader("languageCode") String languageCode) throws QorvaException {
		userDTO.setLanguageCode(languageCode);
		return BuildApiResponse.from(this.userService.createOne(userDTO));
	}
}
