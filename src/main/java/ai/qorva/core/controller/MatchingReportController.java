package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.MatchingRunData;
import ai.qorva.core.dto.PipelineBoardData;
import ai.qorva.core.enums.ReportStatusChannel;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.CrudPolicy;
import ai.qorva.core.service.ATSExportService;
import ai.qorva.core.service.MatchingReportService;
import ai.qorva.core.service.PipelineBoardService;
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
	private final PipelineBoardService pipelineBoardService;

	@Autowired
	public MatchingReportController(MatchingReportService service, ATSExportService atsExportService, UserService userService,
	                                PipelineBoardService pipelineBoardService) {
		super(service);
		this.atsExportService = atsExportService;
		this.userService = userService;
		this.pipelineBoardService = pipelineBoardService;
	}

	/** {@code expectedStatus}: where the caller saw the candidate; a different stored status answers 409. */
	public record StatusRequest(String status, String expectedStatus) {
	}

	/*
	 * Reports are created by matching runs (/ai/matching-runs), never through the generic CRUD: the app
	 * lists, searches, reads one (the pipeline board's side panel) and deletes them. VIEW_REPORT to read,
	 * DELETE_REPORT to delete.
	 */
	@Override
	protected CrudPolicy crudPolicy() {
		return CrudPolicy.builder()
			.allow("VIEW_REPORT", GET_ONE, LIST, SEARCH)
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
			currentTenantId(), id, request == null ? null : request.status(), request == null ? null : request.expectedStatus(),
			actor, ReportStatusChannel.APP));
	}

	/** The pipeline board: every status column with its exact count and first page of cards. */
	@GetMapping("/pipeline")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_REPORT')")
	public ResponseEntity<PipelineBoardData.Board> pipeline(@RequestParam(required = false) String jobPostId,
	                                                        @RequestParam(required = false) String q,
	                                                        @RequestParam(defaultValue = "false") boolean hideOutdated) throws QorvaException {
		return ResponseEntity.ok(pipelineBoardService.board(currentTenantId(), new PipelineBoardService.Filter(jobPostId, q, hideOutdated)));
	}

	/** One more page of a column, after {@code cursor} (from the previous page). */
	@GetMapping("/pipeline/{status}")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_REPORT')")
	public ResponseEntity<PipelineBoardData.Column> pipelineColumn(@PathVariable String status,
	                                                               @RequestParam(required = false) String jobPostId,
	                                                               @RequestParam(required = false) String q,
	                                                               @RequestParam(defaultValue = "false") boolean hideOutdated,
	                                                               @RequestParam(required = false) String cursor,
	                                                               @RequestParam(required = false) Integer size) throws QorvaException {
		return ResponseEntity.ok(pipelineBoardService.column(currentTenantId(), status,
			new PipelineBoardService.Filter(jobPostId, q, hideOutdated), cursor, size));
	}

	@GetMapping("/export/csv")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_REPORT')")
	public ResponseEntity<byte[]> exportCsv(
		@RequestParam String jobPostId,
		@RequestParam(defaultValue = "global") String format) throws QorvaException {
		return atsExportService.exportCsv(currentTenantId(), jobPostId, format);
	}
}
