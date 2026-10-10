package ai.qorva.core.admin.controller;

import ai.qorva.core.admin.dto.AdminJobData;
import ai.qorva.core.admin.dto.AdminOpsData;
import ai.qorva.core.admin.dto.AdminPage;
import ai.qorva.core.admin.dto.AdminStatsData;
import ai.qorva.core.admin.dto.AdminTesterData;
import ai.qorva.core.admin.service.AdminOpsService;
import ai.qorva.core.admin.service.AdminQueryParams;
import ai.qorva.core.admin.service.AdminStatsService;
import ai.qorva.core.admin.service.AdminTesterQueries;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Background jobs, Stripe events, the audit log, statistics and reference data. Every admin; cancelling a job included. */
@RestController
@RequestMapping("/admin")
public class AdminOpsController {

	private final AdminOpsService ops;
	private final AdminStatsService stats;
	private final AdminTesterQueries testers;

	public AdminOpsController(AdminOpsService ops, AdminStatsService stats, AdminTesterQueries testers) {
		this.ops = ops;
		this.stats = stats;
		this.testers = testers;
	}

	@GetMapping("/background-jobs")
	public AdminPage<AdminJobData.JobRow> jobs(@RequestParam Map<String, String> params) throws QorvaException {
		return ops.jobs(params);
	}

	@GetMapping("/background-jobs/{id}")
	public AdminJobData.JobDetail job(@PathVariable String id) throws QorvaException {
		return ops.job(id);
	}

	@PostMapping("/background-jobs/{id}/cancel")
	public AdminJobData.JobDetail cancel(@PathVariable String id) throws QorvaException {
		return ops.cancel(id);
	}

	@GetMapping("/stripe-events")
	public AdminPage<AdminOpsData.StripeEventRow> stripeEvents(@RequestParam Map<String, String> params) throws QorvaException {
		return ops.stripeEvents(params);
	}

	@GetMapping("/stripe-events/types")
	public List<String> stripeEventTypes() {
		return ops.stripeEventTypes();
	}

	@GetMapping("/audit")
	public AdminPage<AdminOpsData.AuditEntry> audit(@RequestParam Map<String, String> params) throws QorvaException {
		return ops.audit(params);
	}

	@GetMapping("/stats/overview")
	public AdminStatsData.Overview overview(@RequestParam(defaultValue = "false") boolean includeInternal) {
		return stats.overview(includeInternal);
	}

	@GetMapping("/stats/{domain}")
	public AdminStatsData.DomainStats domain(@PathVariable String domain, @RequestParam Map<String, String> params) throws QorvaException {
		Instant from = AdminQueryParams.instant(params, "from");
		Instant to = AdminQueryParams.instant(params, "to");
		return stats.domain(domain, from, to, params.get("granularity"), "true".equalsIgnoreCase(params.get("includeInternal")));
	}

	@GetMapping("/tiers")
	public List<AdminTesterData.Tier> tiers() {
		return testers.tiers();
	}

	@GetMapping("/actions")
	public List<String> actions() {
		return Arrays.stream(UserActionsEnum.values()).map(UserActionsEnum::getValue).toList();
	}
}
