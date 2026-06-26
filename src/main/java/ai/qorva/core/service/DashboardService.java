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

	public DashboardData.TopCandidatesPage getTopCandidatesPerJobPost(UserDetails userDetails, int page, int pageSize) throws QorvaException {
		var userInfo = Optional.ofNullable(this.userService.findOneByCriteria(UserDTO.builder().email(userDetails.getUsername()).build()))
			.orElseThrow(() -> new QorvaException("User not found"));
		return this.matchingReportService.getTopCandidatesPerJobPost(userInfo.getTenantId(), page, pageSize);
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

		var skillReports = CompletableFuture.supplyAsync(
			() -> this.cvService.getSkillReportByTenantId(tenantId),
			dashboardExecutor
		);

		var skillDepthReport = CompletableFuture.supplyAsync(
			() -> this.cvService.getSkillDepthReportByTenantId(tenantId),
			dashboardExecutor
		);

		var seniorityLevelReport = CompletableFuture.supplyAsync(
			() -> this.cvService.getSeniorityLevelReportByTenantId(tenantId),
			dashboardExecutor
		);

		var leadershipReport = CompletableFuture.supplyAsync(
			() -> this.cvService.getLeadershipReportByTenantId(tenantId),
			dashboardExecutor
		);

		var learningVelocityReport = CompletableFuture.supplyAsync(
			() -> this.cvService.getLearningVelocityReportByTenantId(tenantId),
			dashboardExecutor
		);

		var jobPostReports = CompletableFuture.supplyAsync(
			() -> this.matchingReportService.getApplicationsPerJobPost(tenantId),
			dashboardExecutor
		);

		totalCvs.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalJobPosts.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalUsers.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalMatchingReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		skillReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		skillDepthReport.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		seniorityLevelReport.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		leadershipReport.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		learningVelocityReport.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		jobPostReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());

		CompletableFuture.allOf(
			totalCvs,
			totalJobPosts,
			totalUsers,
			totalMatchingReports,
			skillReports,
			skillDepthReport,
			seniorityLevelReport,
			leadershipReport,
			learningVelocityReport,
			jobPostReports
		).join();

		return new DashboardData(
			subscriptionStatus,
			totalCvs.join(),
			totalJobPosts.join(),
			totalUsers.join(),
			totalMatchingReports.join(),
			skillReports.join(),
			jobPostReports.join(),
			skillDepthReport.join(),
			seniorityLevelReport.join(),
			leadershipReport.join(),
			learningVelocityReport.join()
		);
	}
}
