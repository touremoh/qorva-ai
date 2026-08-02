package ai.qorva.core.controller;

import ai.qorva.core.dto.CandidateEmailTemplateData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.CandidateEmailTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Recruiter-authored invitation templates for candidate-update campaigns. */
@Slf4j
@RestController
@RequestMapping("/email-templates/candidate-update")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class CandidateEmailTemplateController {

	private final CandidateEmailTemplateService templateService;

	@Autowired
	public CandidateEmailTemplateController(CandidateEmailTemplateService templateService) {
		this.templateService = templateService;
	}

	@GetMapping(produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<CandidateEmailTemplateData.TemplateList> list() {
		return ResponseEntity.ok(this.templateService.list(TenantContextHolder.getTenantId()));
	}

	@PostMapping(produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<CandidateEmailTemplateData.TemplateView> create(
		@RequestBody CandidateEmailTemplateData.SaveRequest request,
		@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		return ResponseEntity.ok(this.templateService.create(
			TenantContextHolder.getTenantId(),
			userDetails != null ? userDetails.getUsername() : null,
			request));
	}

	@PutMapping(path = "/{templateId}", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<CandidateEmailTemplateData.TemplateView> update(
		@PathVariable String templateId,
		@RequestBody CandidateEmailTemplateData.SaveRequest request) throws QorvaException {
		return ResponseEntity.ok(this.templateService.update(TenantContextHolder.getTenantId(), templateId, request));
	}

	@DeleteMapping(path = "/{templateId}")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<Void> delete(@PathVariable String templateId) throws QorvaException {
		this.templateService.delete(TenantContextHolder.getTenantId(), templateId);
		return ResponseEntity.noContent().build();
	}

	/** Renders a draft (saved or not) with sample data — used by the editor's live preview. */
	@PostMapping(path = "/preview", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<CandidateEmailTemplateData.PreviewResponse> preview(
		@RequestBody CandidateEmailTemplateData.PreviewRequest request,
		@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		return ResponseEntity.ok(this.templateService.preview(
			TenantContextHolder.getTenantId(),
			userDetails != null ? userDetails.getUsername() : null,
			request));
	}

	/** Sends the template to the calling user's own mailbox with a dead link. */
	@PostMapping(path = "/{templateId}/test")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<Void> sendTest(
		@PathVariable String templateId,
		@RequestParam(defaultValue = "en") String language,
		@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		this.templateService.sendTest(
			TenantContextHolder.getTenantId(),
			templateId,
			userDetails != null ? userDetails.getUsername() : null,
			language);
		return ResponseEntity.noContent().build();
	}
}
