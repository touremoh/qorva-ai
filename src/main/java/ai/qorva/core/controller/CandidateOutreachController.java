package ai.qorva.core.controller;

import ai.qorva.core.dto.CandidateOutreachDTO;
import ai.qorva.core.dto.CandidateOutreachData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.CandidateOutreachDraftService;
import ai.qorva.core.service.CandidateOutreachService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emailing a candidate from the CV list or a matching report: the composer's context, the AI
 * draft, sending through the recruiter's connected mailbox, and recording hand-offs to their
 * own mail client. Every route needs CONTACT_CANDIDATE — demo users never hold it.
 */
@RestController
@RequestMapping("/candidate-outreach")
@RequiredArgsConstructor
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class CandidateOutreachController {

	private final CandidateOutreachService outreachService;
	private final CandidateOutreachDraftService draftService;

	private String currentTenantId() {
		return TenantContextHolder.getTenantId();
	}

	private String currentUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	@GetMapping("/context")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'CONTACT_CANDIDATE')")
	public ResponseEntity<CandidateOutreachData.ContextResponse> context(@RequestParam String cvId) throws QorvaException {
		return ResponseEntity.ok(outreachService.context(currentTenantId(), currentUsername(), cvId));
	}

	@PostMapping("/draft")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'CONTACT_CANDIDATE')")
	public ResponseEntity<CandidateOutreachData.DraftResponse> draft(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@RequestBody @Valid CandidateOutreachData.DraftRequest request) throws QorvaException {
		return ResponseEntity.ok(draftService.draft(currentTenantId(), currentUsername(), request, language));
	}

	@PostMapping("/send")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'CONTACT_CANDIDATE')")
	public ResponseEntity<CandidateOutreachData.SendResponse> send(
		@RequestBody @Valid CandidateOutreachData.SendRequest request) throws QorvaException {
		return ResponseEntity.ok(outreachService.send(currentTenantId(), currentUsername(), request));
	}

	@PostMapping("/external")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'CONTACT_CANDIDATE')")
	public ResponseEntity<CandidateOutreachDTO> external(
		@RequestBody @Valid CandidateOutreachData.ExternalRequest request) throws QorvaException {
		var created = outreachService.recordExternal(currentTenantId(), currentUsername(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}
}
