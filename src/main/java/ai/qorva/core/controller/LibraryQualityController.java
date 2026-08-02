package ai.qorva.core.controller;

import ai.qorva.core.dto.BackgroundJobData;
import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.BackgroundJobService;
import ai.qorva.core.service.LibraryQualityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/library-quality")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class LibraryQualityController {

	private final LibraryQualityService libraryQualityService;
	private final BackgroundJobService backgroundJobService;

	@Autowired
	public LibraryQualityController(LibraryQualityService libraryQualityService, BackgroundJobService backgroundJobService) {
		this.libraryQualityService = libraryQualityService;
		this.backgroundJobService = backgroundJobService;
	}

	@GetMapping(produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<LibraryQualityReport> getReport() {
		return ResponseEntity.ok(this.libraryQualityService.getReport(TenantContextHolder.getTenantId()));
	}

	/**
	 * Sidebar-badge summary. Derives from {@code getReport} through the Spring proxy so it
	 * always hits the per-tenant cache — sidebar traffic must never add report-computation load.
	 */
	@GetMapping(path = "/summary", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<LibraryQualityReport.Summary> getSummary() {
		var report = this.libraryQualityService.getReport(TenantContextHolder.getTenantId());
		var openIssues = (int) report.issues().stream().filter(i -> !i.dismissed()).count();
		return ResponseEntity.ok(new LibraryQualityReport.Summary(openIssues));
	}

	/** Bulk remediation: ARCHIVE (criteria or ids), UNARCHIVE (ids), CONFIRM_CURRENT (ids, capped). */
	@PostMapping(path = "/actions", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<LibraryQualityReport.ActionResult> performAction(
		@RequestBody LibraryQualityReport.ActionRequest request) throws QorvaException {
		return ResponseEntity.ok(this.libraryQualityService.performAction(TenantContextHolder.getTenantId(), request));
	}

	@PostMapping(path = "/issues/{issueKey}/dismiss")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<Void> dismissIssue(
		@PathVariable String issueKey,
		@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		this.libraryQualityService.dismissIssue(TenantContextHolder.getTenantId(), issueKey,
			userDetails != null ? userDetails.getUsername() : null);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(path = "/issues/{issueKey}/reopen")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<Void> reopenIssue(@PathVariable String issueKey) throws QorvaException {
		this.libraryQualityService.reopenIssue(TenantContextHolder.getTenantId(), issueKey);
		return ResponseEntity.noContent().build();
	}

	// -------------------------------------------------------------------------
	// Background jobs (async bulk operations, e.g. re-analyze)
	// -------------------------------------------------------------------------

	@PostMapping(path = "/jobs", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<BackgroundJobData.SubmitResponse> submitJob(
		@RequestBody BackgroundJobData.SubmitRequest request,
		@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		return ResponseEntity.ok(this.backgroundJobService.submit(
			TenantContextHolder.getTenantId(),
			userDetails != null ? userDetails.getUsername() : null,
			request));
	}

	@GetMapping(path = "/jobs", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<BackgroundJobData.JobList> listJobs() {
		return ResponseEntity.ok(this.backgroundJobService.list(TenantContextHolder.getTenantId()));
	}

	@GetMapping(path = "/jobs/{jobId}", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<BackgroundJobData.JobView> getJob(@PathVariable String jobId) throws QorvaException {
		return ResponseEntity.ok(this.backgroundJobService.get(TenantContextHolder.getTenantId(), jobId));
	}

	@PostMapping(path = "/jobs/{jobId}/cancel", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'MODIFY_CV')")
	public ResponseEntity<BackgroundJobData.JobView> cancelJob(@PathVariable String jobId) throws QorvaException {
		return ResponseEntity.ok(this.backgroundJobService.cancel(TenantContextHolder.getTenantId(), jobId));
	}

	@GetMapping(path = "/issues", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_CV')")
	public ResponseEntity<LibraryQualityReport.IssueCVPage> getIssueCVs(
		@RequestParam QualityIssueKeyEnum issueKey,
		@RequestParam(defaultValue = "0") int pageNumber,
		@RequestParam(defaultValue = "20") int pageSize) throws QorvaException {
		return ResponseEntity.ok(this.libraryQualityService.getIssueCVs(
			TenantContextHolder.getTenantId(), issueKey, pageNumber, Math.min(Math.max(pageSize, 1), 100)));
	}
}
