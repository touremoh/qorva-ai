package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.request.UpgradeRequestDTO;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.UserRegistrationService;
import ai.qorva.core.service.UserService;
import ai.qorva.core.utils.BuildApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Authenticated subscription actions. Notably lets a demo user upgrade to a paid plan (14-day trial)
 * by adding a card — this route is intentionally NOT under the public /registrations path.
 */
@RestController
@RequestMapping("/subscriptions")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class SubscriptionController {

	private final UserRegistrationService registrationService;
	private final UserService userService;

	@Autowired
	public SubscriptionController(UserRegistrationService registrationService, UserService userService) {
		this.registrationService = registrationService;
		this.userService = userService;
	}

	@PostMapping(path = "/upgrade", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> upgrade(@RequestBody @Valid UpgradeRequestDTO request) throws QorvaException {
		var tenantId = TenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			throw new QorvaException(QorvaErrorCodes.HTTP_UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		var email = SecurityContextHolder.getContext().getAuthentication().getName();
		var user = userService.findByEmail(email);
		if (user == null) {
			throw new QorvaException(QorvaErrorCodes.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND);
		}

		return BuildApiResponse.from(registrationService.upgrade(tenantId, user.getId(), request.getPriceId()));
	}
}
