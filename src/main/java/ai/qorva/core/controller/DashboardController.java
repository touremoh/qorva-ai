package ai.qorva.core.controller;

import ai.qorva.core.utils.Paging;

import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.dto.PipelineDashboardData;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.DashboardService;
import ai.qorva.core.service.PipelineDashboardService;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/dashboard")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class DashboardController {

	private final DashboardService dashboardService;
	private final PipelineDashboardService pipelineDashboardService;

	@Autowired
	public DashboardController(DashboardService dashboardService, PipelineDashboardService pipelineDashboardService) {
		this.dashboardService = dashboardService;
		this.pipelineDashboardService = pipelineDashboardService;
	}

	/** Pipeline card: current status counts and each recruiter's moves in the period (default: the last 30 days). */
	@GetMapping(path = "/pipeline", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<PipelineDashboardData> getPipeline(
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
		@RequestParam(required = false) String jobPostId) throws QorvaException {
		return ResponseEntity.ok(pipelineDashboardService.pipeline(TenantContextHolder.getTenantId(), from, to, jobPostId));
	}

	@GetMapping(path = "/data", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<DashboardData> getDashboardData(@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		return ResponseEntity.ok(this.dashboardService.getDashboardData(userDetails));
	}

	@GetMapping(path = "/top-candidates", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<DashboardData.TopCandidatesPage> getTopCandidatesPerJobPost(
		@AuthenticationPrincipal UserDetails userDetails,
		@RequestParam(defaultValue = "0") int pageNumber,
		@RequestParam(defaultValue = "5") int pageSize) throws QorvaException {
		return ResponseEntity.ok(this.dashboardService.getTopCandidatesPerJobPost(userDetails, Paging.page(pageNumber), Paging.size(pageSize)));
	}
}
