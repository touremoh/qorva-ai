package ai.qorva.core.controller;

import ai.qorva.core.dto.UsageInsight;
import ai.qorva.core.dto.UsageMonitoringDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.BulkCvUploadService;
import ai.qorva.core.service.UsageForecaster;
import ai.qorva.core.service.UsageInsightService;
import ai.qorva.core.service.UsageMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/usage-monitoring")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class UsageMonitoringController {

	private final UsageMonitoringService usageMonitoringService;
	private final BulkCvUploadService bulkCvUploadService;
	private final UsageInsightService usageInsightService;

	@Autowired
	public UsageMonitoringController(UsageMonitoringService usageMonitoringService, BulkCvUploadService bulkCvUploadService,
	                                 UsageInsightService usageInsightService) {
		this.usageMonitoringService = usageMonitoringService;
		this.bulkCvUploadService = bulkCvUploadService;
		this.usageInsightService = usageInsightService;
	}

	/** The current billing period, with the plan's bulk-upload cap, the billing cycle and each meter's pace (all per request). */
	@GetMapping(path = "/current", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<UsageMonitoringDTO> getCurrentUsageMonitoring() throws QorvaException {
		var tenantId = TenantContextHolder.getTenantId();
		var usage = this.usageMonitoringService.findCurrentPeriodByTenantId(tenantId).orElse(null);
		if (usage == null) {
			// No active period for this tenant: say so explicitly rather than a 200 with an empty body.
			return ResponseEntity.noContent().build();
		}
		usage.setBulkUploadFilesLimit(bulkCvUploadService.maxFilesForTenant(tenantId));
		usage.setBillingCycle(usageInsightService.billingCycleOf(tenantId));
		usage.setForecast(UsageForecaster.forecast(usage, Instant.now()));
		return ResponseEntity.ok(usage);
	}

	/**
	 * AI summary of the current period in the caller's language: 204 without an active period, 503
	 * ({@code error.usage.insight_unavailable}) when the model cannot answer.
	 */
	@GetMapping(path = "/insight", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication, 'VIEW_DASHBOARD')")
	public ResponseEntity<UsageInsight> getInsight(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language) throws QorvaException {
		return this.usageInsightService.getInsight(TenantContextHolder.getTenantId(), language)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
