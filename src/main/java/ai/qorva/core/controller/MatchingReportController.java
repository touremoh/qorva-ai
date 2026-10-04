package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.MatchingRunData;
import ai.qorva.core.enums.ReportStatusChannel;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.CrudPolicy;
import ai.qorva.core.service.ATSExportService;
import ai.qorva.core.service.MatchingReportService;
import ai.qorva.core.service.UserService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static ai.qorva.core.security.CrudOperation.*;

@RestController
@RequestMapping("/matching-reports")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class MatchingReportController extends AbstractQorvaController<MatchingReportDTO> {

	private final ATSExportService atsExportService;
	private final UserService userService;

	@Autowired
	public MatchingReportController(MatchingReportService service, ATSExportService atsExportService, UserService userService) {
		super(service);
		this.atsExportService = atsExportService;
		this.userService = userService;
	}

	public record StatusRequest(String status) {
	}

	/*
	 * Reports are created by matching runs (/ai/matching-runs), never through the generic CRUD: the app
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

	/** Deletes the job's outdated reports (with their notes and chats) in one go. */
	@DeleteMapping("/outdated")
	@PreAuthorize("@accessManager.hasPermission(authentication,'DELETE_REPORT')")
	public ResponseEntity<MatchingRunData.DeleteOutdatedResponse> deleteOutdated(@RequestParam String jobPostId) throws QorvaException {
		return ResponseEntity.ok(new MatchingRunData.DeleteOutdatedResponse(
			((MatchingReportService) this.service).deleteOutdated(currentTenantId(), jobPostId)));
	}

	/** Moves the candidate along the pipeline on this job (New → Contacted → … → Hired, or Rejected/Withdrawn). */
	@PatchMapping("/{id}/status")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MODIFY_REPORT')")
	public ResponseEntity<MatchingReportDTO> changeStatus(@PathVariable String id, @RequestBody StatusRequest request) throws QorvaException {
		var email = SecurityContextHolder.getContext().getAuthentication().getName();
		var actor = MatchingReportService.StatusActor.of(userService.findByEmail(email), email);
		return ResponseEntity.ok(((MatchingReportService) this.service).changeStatus(
			currentTenantId(), id, request == null ? null : request.status(), actor, ReportStatusChannel.APP));
	}

	@GetMapping("/export/csv")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_REPORT')")
	public ResponseEntity<byte[]> exportCsv(
		@RequestParam String jobPostId,
		@RequestParam(defaultValue = "global") String format) throws QorvaException {
		return atsExportService.exportCsv(currentTenantId(), jobPostId, format);
	}
}
