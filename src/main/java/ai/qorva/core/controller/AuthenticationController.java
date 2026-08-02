package ai.qorva.core.controller;


import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.request.ResendSetPasswordRequest;
import ai.qorva.core.dto.request.SetPasswordRequest;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.AuthenticationService;
import ai.qorva.core.service.SetPasswordService;
import ai.qorva.core.utils.BuildApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class AuthenticationController {

	private final AuthenticationService authenticationService;
	private final SetPasswordService setPasswordService;

	@Autowired
	public AuthenticationController(AuthenticationService authenticationService, SetPasswordService setPasswordService) {
		this.authenticationService = authenticationService;
		this.setPasswordService = setPasswordService;
	}

	@PostMapping(path = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> authenticate(@RequestBody UserDTO userDTO) throws QorvaException {
		return BuildApiResponse.from(this.authenticationService.authenticate(userDTO));
	}

	@PostMapping(path = "/token/validate", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> validateToken(@RequestHeader("Authorization") String authorizationHeader) throws QorvaException {
		return BuildApiResponse.from(this.authenticationService.isTokenValid(authorizationHeader));
	}

	@PostMapping(path = "/token/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> refresh(@RequestHeader("Authorization") String authorizationHeader) throws QorvaException {
		return BuildApiResponse.from(this.authenticationService.refreshToken(authorizationHeader));
	}

	@PostMapping(path = "/password/set", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> setPassword(@RequestBody @Valid SetPasswordRequest request) throws QorvaException {
		this.setPasswordService.setPassword(request.getToken(), request.getNewPassword());
		return BuildApiResponse.from(true);
	}

	@PostMapping(path = "/password/resend", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> resendSetPassword(@RequestBody @Valid ResendSetPasswordRequest request) {
		this.setPasswordService.resend(request.getEmail());
		// Always return success to avoid leaking which emails are registered
		return BuildApiResponse.from(true);
	}
}
