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

import static ai.qorva.core.service.AsyncFallbacks.withFallback;

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

		var totalCvs = withFallback(CompletableFuture.supplyAsync(() -> {
			try { return this.cvService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor), TIMEOUT_SECONDS, 0L, "totalCVs");

		var totalJobPosts = withFallback(CompletableFuture.supplyAsync(() -> {
			try { return this.jobPostService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor), TIMEOUT_SECONDS, 0L, "totalJobsPosted");

		var totalMatchingReports = withFallback(CompletableFuture.supplyAsync(() -> {
			try { return this.matchingReportService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor), TIMEOUT_SECONDS, 0L, "totalResumeAnalysis");

		var totalUsers = withFallback(CompletableFuture.supplyAsync(() -> {
			try { return this.userService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor), TIMEOUT_SECONDS, 0L, "totalUsers");

		CompletableFuture<List<DashboardData.SkillReport>> skillReports = withFallback(CompletableFuture.supplyAsync(
			() -> this.cvService.getSkillReportByTenantId(tenantId),
			dashboardExecutor
		), TIMEOUT_SECONDS, List.of(), "skillsReport");

		CompletableFuture<List<DashboardData.ClusteringCategoryReport>> skillDepthReport = withFallback(CompletableFuture.supplyAsync(
			() -> this.cvService.getSkillDepthReportByTenantId(tenantId),
			dashboardExecutor
		), TIMEOUT_SECONDS, List.of(), "skillDepthReport");

		CompletableFuture<List<DashboardData.ClusteringCategoryReport>> seniorityLevelReport = withFallback(CompletableFuture.supplyAsync(
			() -> this.cvService.getSeniorityLevelReportByTenantId(tenantId),
			dashboardExecutor
		), TIMEOUT_SECONDS, List.of(), "seniorityLevelReport");

		CompletableFuture<List<DashboardData.ClusteringCategoryReport>> leadershipReport = withFallback(CompletableFuture.supplyAsync(
			() -> this.cvService.getLeadershipReportByTenantId(tenantId),
			dashboardExecutor
		), TIMEOUT_SECONDS, List.of(), "leadershipReport");

		CompletableFuture<List<DashboardData.ClusteringCategoryReport>> learningVelocityReport = withFallback(CompletableFuture.supplyAsync(
			() -> this.cvService.getLearningVelocityReportByTenantId(tenantId),
			dashboardExecutor
		), TIMEOUT_SECONDS, List.of(), "learningVelocityReport");

		CompletableFuture<List<DashboardData.ApplicationPerJobPostReport>> jobPostReports = withFallback(CompletableFuture.supplyAsync(
			() -> this.matchingReportService.getApplicationsPerJobPost(tenantId),
			dashboardExecutor
		), TIMEOUT_SECONDS, List.of(), "jobPostsReport");

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
