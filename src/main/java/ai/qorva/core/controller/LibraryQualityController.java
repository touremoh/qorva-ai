package ai.qorva.core.controller;

import ai.qorva.core.dto.LibraryQualityReport;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.LibraryQualityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/library-quality")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class LibraryQualityController {

	private final LibraryQualityService libraryQualityService;

	@Autowired
	public LibraryQualityController(LibraryQualityService libraryQualityService) {
		this.libraryQualityService = libraryQualityService;
	}

	@GetMapping(produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<LibraryQualityReport> getReport() {
		return ResponseEntity.ok(this.libraryQualityService.getReport(TenantContextHolder.getTenantId()));
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
