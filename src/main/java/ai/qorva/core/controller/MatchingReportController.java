package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.CrudPolicy;
import ai.qorva.core.service.ATSExportService;
import ai.qorva.core.service.MatchingReportService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static ai.qorva.core.security.CrudOperation.*;

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
	 * Reports are created by screening (/ai/start-screening), never through the generic CRUD: the app
	 * lists, searches and deletes them. VIEW_REPORT to read, DELETE_REPORT to delete.
	 */
	@Override
	protected CrudPolicy crudPolicy() {
		return CrudPolicy.builder()
			.allow("VIEW_REPORT", LIST, SEARCH)
			.allow("DELETE_REPORT", DELETE)
			.build();
	}

	@GetMapping("/search")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_REPORT')")
	public ResponseEntity<QorvaRequestResponse> searchAll(@RequestParam Map<String, String> params) throws QorvaException {
		params.put("tenantId", currentTenantId());
		return BuildApiResponse.from(((MatchingReportService) this.service).searchAll(params));
	}

	@GetMapping("/export/csv")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_REPORT')")
	public ResponseEntity<byte[]> exportCsv(
		@RequestParam String jobPostId,
		@RequestParam(defaultValue = "global") String format) throws QorvaException {
		return atsExportService.exportCsv(currentTenantId(), jobPostId, format);
	}
}
