package ai.qorva.core.controller;

import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.DashboardService;
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

	@Autowired
	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
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
		return ResponseEntity.ok(this.dashboardService.getTopCandidatesPerJobPost(userDetails, pageNumber, pageSize));
	}
}
