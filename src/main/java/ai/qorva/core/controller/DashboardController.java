package ai.qorva.core.controller;

import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
	public ResponseEntity<DashboardData> getDashboardData(@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		return ResponseEntity.ok(this.dashboardService.getDashboardData(userDetails));
	}
}
