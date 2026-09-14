package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ATSExportService;
import ai.qorva.core.service.MatchingReportService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/matching-reports")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class MatchingReportController extends AbstractQorvaController<MatchingReportDTO> {

	private final ATSExportService atsExportService;

	@Autowired
	public MatchingReportController(MatchingReportService service, ATSExportService atsExportService) {
		super(service);
		this.atsExportService = atsExportService;
	}

	/*
	 * The inherited write routes carry the report authorities; the generic CRUD base leaves them
	 * open, and demo users only hold GENERATE_REPORT / VIEW_REPORT.
	 */

	@Override
	@PostMapping
	@PreAuthorize("@accessManager.hasPermission(authentication,'GENERATE_REPORT')")
	public ResponseEntity<QorvaRequestResponse> createOne(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@RequestBody MatchingReportDTO data) throws QorvaException {
		return super.createOne(language, data);
	}

	@Override
	@PutMapping("/{id}")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MODIFY_REPORT')")
	public ResponseEntity<QorvaRequestResponse> updateOne(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@PathVariable String id,
		@RequestBody MatchingReportDTO data) throws QorvaException {
		return super.updateOne(language, id, data);
	}

	@Override
	@PatchMapping("/{id}")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MODIFY_REPORT')")
	public ResponseEntity<QorvaRequestResponse> patchOne(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@PathVariable String id,
		@RequestBody MatchingReportDTO data) throws QorvaException {
		return super.patchOne(language, id, data);
	}

	@Override
	@DeleteMapping("/{id}")
	@PreAuthorize("@accessManager.hasPermission(authentication,'DELETE_REPORT')")
	public ResponseEntity<QorvaRequestResponse> deleteOneById(@PathVariable String id) throws QorvaException {
		return super.deleteOneById(id);
	}

	@GetMapping("/check/monthly-usage")
	public ResponseEntity<QorvaRequestResponse> checkCVAnalysisMonthlyUsageLimit() throws QorvaException {
		return null;
	}

	@GetMapping("/search")
	public ResponseEntity<QorvaRequestResponse> searchAll(@RequestParam Map<String, String> params) throws QorvaException {
		params.put("tenantId", currentTenantId());
		return BuildApiResponse.from(((MatchingReportService) this.service).searchAll(params));
	}

	@GetMapping("/export/csv")
	public ResponseEntity<byte[]> exportCsv(
		@RequestParam String jobPostId,
		@RequestParam(defaultValue = "global") String format) throws QorvaException {
		return atsExportService.exportCsv(currentTenantId(), jobPostId, format);
	}
}
