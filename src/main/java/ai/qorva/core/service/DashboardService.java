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
	private final ResumeMatchService resumeMatchService;
	private final JobPostService jobPostService;
	private final CVService cvService;
	private final TenantService tenantService;
	private final ExecutorService dashboardExecutor;

	private static final int TIMEOUT_SECONDS = 15;

	@Autowired
	public DashboardService(UserService userService, ResumeMatchService resumeMatchService, JobPostService jobPostService, CVService cvService, TenantService tenantService, ExecutorService dashboardExecutor) {
		this.userService = userService;
		this.resumeMatchService = resumeMatchService;
		this.jobPostService = jobPostService;
		this.cvService = cvService;
		this.tenantService = tenantService;
		this.dashboardExecutor = dashboardExecutor;
	}

	public DashboardData getDashboardData(UserDetails userDetails) throws QorvaException {
		// Find the user info
		var userInfo = Optional.ofNullable(this.userService.findOneByData(UserDTO.builder().email(userDetails.getUsername()).build()))
			                   .orElseThrow(() -> new QorvaException("User not found"));

		// Find tenant info
		var tenantInfo = this.tenantService.findOneById(userInfo.getTenantId());

		// Get subscription status
		var subscriptionStatus = tenantInfo.getSubscriptionInfo().getSubscriptionStatus();

		// Get total CVs
		var totalCvs = CompletableFuture.supplyAsync(() -> {
			try {
				return this.cvService.countAll(userInfo.getTenantId());
			} catch (QorvaException e) {
				throw new RuntimeException(e);
			}
		}, dashboardExecutor);

		// Get total job posts
		var totalJobPosts = CompletableFuture.supplyAsync(() -> {
			try {
				return this.jobPostService.countAll(userInfo.getTenantId());
			} catch (QorvaException e) {
				throw new RuntimeException(e);
			}
		}, dashboardExecutor);

		// Get total resume matches
		var totalResumeMatches = CompletableFuture.supplyAsync(() -> {
			try {
				return this.resumeMatchService.countAll(userInfo.getTenantId());
			} catch (QorvaException e) {
				throw new RuntimeException(e);
			}
		}, dashboardExecutor);

		// Get total users
		var totalUsers = CompletableFuture.supplyAsync(() -> {
			try {
				return this.userService.countAll(userInfo.getTenantId());
			} catch (QorvaException e) {
				throw new RuntimeException(e);
			}
		}, dashboardExecutor);

		// Get total resumes processed in the current month
		var totalResumesProcessedInCurrentMonth = CompletableFuture.supplyAsync(() ->
			this.resumeMatchService.countResumeMatchesInCurrentMonth(userInfo.getTenantId()),
			dashboardExecutor
		);

		// Get skill reports
		var skillReports = CompletableFuture.supplyAsync(() -> this.cvService.getSkillReportByTenantId(userInfo.getTenantId()), dashboardExecutor);

		// Get Job posts reports
		var jobPostReports = CompletableFuture.supplyAsync(() -> this.resumeMatchService.getApplicationsPerJobPost(userInfo.getTenantId()), dashboardExecutor);

		// timeouts & graceful fallbacks
		totalCvs.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalJobPosts.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalUsers.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalResumeMatches.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalResumesProcessedInCurrentMonth.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		skillReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		jobPostReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());

		CompletableFuture.allOf(
			totalCvs,
			totalJobPosts,
			totalUsers,
			totalResumeMatches,
			totalResumesProcessedInCurrentMonth,
			skillReports,
			jobPostReports
		).join();

		// Build the dashboard data
		return new DashboardData(
			subscriptionStatus,
			totalCvs.join(),
			totalJobPosts.join(),
			totalUsers.join(),
			totalResumeMatches.join(),
			totalResumesProcessedInCurrentMonth.join(),
			skillReports.join(),
			jobPostReports.join()
		);
	}
}
