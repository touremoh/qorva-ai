package ai.qorva.core.controller;

import ai.qorva.core.dto.MfaData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.MfaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own email MFA (Account settings › Profile). Always the signed-in user — never a
 * path id — and every switch needs a code emailed to the account, so a stolen session alone can
 * neither turn MFA off nor turn it on against a mailbox the owner does not read.
 */
@RestController
@RequestMapping("/users/me/mfa")
@RequiredArgsConstructor
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class MfaSettingsController {

	private final MfaService mfaService;

	private String currentTenantId() {
		return TenantContextHolder.getTenantId();
	}

	private String currentUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MfaData.Status> status() throws QorvaException {
		return ResponseEntity.ok(mfaService.status(currentTenantId(), currentUsername()));
	}

	@PostMapping("/enable/start")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MfaData.Challenge> startEnable() throws QorvaException {
		return ResponseEntity.ok(mfaService.startChange(currentTenantId(), currentUsername(), true));
	}

	@PostMapping("/enable/confirm")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MfaData.Status> confirmEnable(@RequestBody @Valid MfaData.VerifyRequest request) throws QorvaException {
		return ResponseEntity.ok(mfaService.confirmChange(currentTenantId(), currentUsername(), true,
			request.getChallengeId(), request.getCode()));
	}

	@PostMapping("/disable/start")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MfaData.Challenge> startDisable() throws QorvaException {
		return ResponseEntity.ok(mfaService.startChange(currentTenantId(), currentUsername(), false));
	}

	@PostMapping("/disable/confirm")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MfaData.Status> confirmDisable(@RequestBody @Valid MfaData.VerifyRequest request) throws QorvaException {
		return ResponseEntity.ok(mfaService.confirmChange(currentTenantId(), currentUsername(), false,
			request.getChallengeId(), request.getCode()));
	}

	@PostMapping("/resend")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MfaData.Challenge> resend(@RequestBody @Valid MfaData.ResendRequest request) throws QorvaException {
		return ResponseEntity.ok(mfaService.resendChange(currentTenantId(), currentUsername(), request.getChallengeId()));
	}
}
