package ai.qorva.core.service;

import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DashboardService {
	private final UserService userService;
	private final MatchingReportService matchingReportService;
	private final JobPostService jobPostService;
	private final CVService cvService;
	private final TenantService tenantService;
	private final UsageMonitoringService usageMonitoringService;
	private final ExecutorService dashboardExecutor;

	private static final int TIMEOUT_SECONDS = 15;

	@Autowired
	public DashboardService(UserService userService, MatchingReportService matchingReportService, JobPostService jobPostService, CVService cvService, TenantService tenantService, UsageMonitoringService usageMonitoringService, ExecutorService dashboardExecutor) {
		this.userService = userService;
		this.matchingReportService = matchingReportService;
		this.jobPostService = jobPostService;
		this.cvService = cvService;
		this.tenantService = tenantService;
		this.usageMonitoringService = usageMonitoringService;
		this.dashboardExecutor = dashboardExecutor;
	}

	public DashboardData getDashboardData(UserDetails userDetails) throws QorvaException {
		var userInfo = Optional.ofNullable(this.userService.findOneByCriteria(UserDTO.builder().email(userDetails.getUsername()).build()))
			.orElseThrow(() -> new QorvaException("User not found"));

		var tenantInfo = this.tenantService.findOneById(userInfo.getTenantId());
		var subscriptionStatus = tenantInfo.getSubscriptionInfo().getSubscriptionStatus();
		var tenantId = userInfo.getTenantId();

		var totalCvs = CompletableFuture.supplyAsync(() -> {
			try { return this.cvService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor);

		var totalJobPosts = CompletableFuture.supplyAsync(() -> {
			try { return this.jobPostService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor);

		var totalMatchingReports = CompletableFuture.supplyAsync(() -> {
			try { return this.matchingReportService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor);

		var totalUsers = CompletableFuture.supplyAsync(() -> {
			try { return this.userService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor);

		var usageMonitoring = CompletableFuture.supplyAsync(
			() -> this.usageMonitoringService.findCurrentPeriodByTenantId(tenantId).orElse(null),
			dashboardExecutor
		);

		var skillReports = CompletableFuture.supplyAsync(
			() -> this.cvService.getSkillReportByTenantId(tenantId),
			dashboardExecutor
		);

		var jobPostReports = CompletableFuture.supplyAsync(
			() -> this.matchingReportService.getApplicationsPerJobPost(tenantId),
			dashboardExecutor
		);

		var topCandidatesPerJob = CompletableFuture.supplyAsync(
			() -> this.matchingReportService.getTopCandidatesPerJobPost(tenantId),
			dashboardExecutor
		);

		totalCvs.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalJobPosts.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalUsers.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalMatchingReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		usageMonitoring.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> null);
		skillReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		jobPostReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		topCandidatesPerJob.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());

		CompletableFuture.allOf(
			totalCvs,
			totalJobPosts,
			totalUsers,
			totalMatchingReports,
			usageMonitoring,
			skillReports,
			jobPostReports,
			topCandidatesPerJob
		).join();

		return new DashboardData(
			subscriptionStatus,
			totalCvs.join(),
			totalJobPosts.join(),
			totalUsers.join(),
			totalMatchingReports.join(),
			usageMonitoring.join(),
			skillReports.join(),
			jobPostReports.join(),
			topCandidatesPerJob.join()
		);
	}
}
