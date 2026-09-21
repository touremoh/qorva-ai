package ai.qorva.core.controller;

import ai.qorva.core.dto.MailboxConnectionData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.mailbox.MailboxConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own connected mailbox (Account settings › Profile). Any authenticated user may
 * connect their own mailbox; whether they may use it on candidates is CONTACT_CANDIDATE's call
 * on {@code /candidate-outreach}. Token material never leaves the service layer.
 */
@RestController
@RequestMapping("/mailbox-connections")
@RequiredArgsConstructor
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class MailboxConnectionController {

	private final MailboxConnectionService connectionService;

	private String currentTenantId() {
		return TenantContextHolder.getTenantId();
	}

	private String currentUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	@GetMapping("/availability")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MailboxConnectionData.Availability> availability() {
		return ResponseEntity.ok(connectionService.availability());
	}

	/** 200 with the connection, 204 when none. */
	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MailboxConnectionData.View> me() {
		return connectionService.view(currentTenantId(), currentUsername())
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/oauth/start")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MailboxConnectionData.OauthStartResponse> startOauth(
		@RequestBody @Valid MailboxConnectionData.OauthStartRequest request) throws QorvaException {
		var url = connectionService.startOauth(currentTenantId(), currentUsername(), request.getProvider());
		return ResponseEntity.ok(new MailboxConnectionData.OauthStartResponse(url));
	}

	@DeleteMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Void> disconnect() {
		connectionService.disconnect(currentTenantId(), currentUsername());
		return ResponseEntity.noContent().build();
	}
}
