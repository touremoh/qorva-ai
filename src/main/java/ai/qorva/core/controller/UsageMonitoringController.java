package ai.qorva.core.controller;

import ai.qorva.core.dto.UsageMonitoringDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.BulkCvUploadService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/usage-monitoring")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class UsageMonitoringController {

	private final UsageMonitoringService usageMonitoringService;
	private final UserService userService;
	private final BulkCvUploadService bulkCvUploadService;

	@Autowired
	public UsageMonitoringController(UsageMonitoringService usageMonitoringService, UserService userService,
	                                 BulkCvUploadService bulkCvUploadService) {
		this.usageMonitoringService = usageMonitoringService;
		this.userService = userService;
		this.bulkCvUploadService = bulkCvUploadService;
	}

	@GetMapping(path = "/current", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<UsageMonitoringDTO> getCurrentUsageMonitoring(@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		var userInfo = Optional.ofNullable(this.userService.findOneByCriteria(UserDTO.builder().email(userDetails.getUsername()).build()))
			.orElseThrow(() -> new QorvaException("User not found"));
		var usage = this.usageMonitoringService.findCurrentPeriodByTenantId(userInfo.getTenantId()).orElse(null);
		if (usage != null) {
			usage.setBulkUploadFilesLimit(bulkCvUploadService.maxFilesForTenant(userInfo.getTenantId()));
		}
		return ResponseEntity.ok(usage);
	}
}
