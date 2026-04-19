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
		var userInfo = Optional.ofNullable(this.userService.findOneByData(UserDTO.builder().email(userDetails.getUsername()).build()))
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

		var totalResumeMatches = CompletableFuture.supplyAsync(() -> {
			try { return this.resumeMatchService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor);

		var totalUsers = CompletableFuture.supplyAsync(() -> {
			try { return this.userService.countAll(tenantId); }
			catch (QorvaException e) { throw new RuntimeException(e); }
		}, dashboardExecutor);

		var totalResumesProcessedInCurrentMonth = CompletableFuture.supplyAsync(
			() -> this.resumeMatchService.countResumeMatchesInCurrentMonth(tenantId),
			dashboardExecutor
		);

		var skillReports = CompletableFuture.supplyAsync(
			() -> this.cvService.getSkillReportByTenantId(tenantId),
			dashboardExecutor
		);

		var jobPostReports = CompletableFuture.supplyAsync(
			() -> this.resumeMatchService.getApplicationsPerJobPost(tenantId),
			dashboardExecutor
		);

		var topCandidatesPerJob = CompletableFuture.supplyAsync(
			() -> this.resumeMatchService.getTopCandidatesPerJobPost(tenantId),
			dashboardExecutor
		);

		totalCvs.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalJobPosts.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalUsers.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalResumeMatches.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		totalResumesProcessedInCurrentMonth.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> 0L);
		skillReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		jobPostReports.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());
		topCandidatesPerJob.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> List.of());

		CompletableFuture.allOf(
			totalCvs,
			totalJobPosts,
			totalUsers,
			totalResumeMatches,
			totalResumesProcessedInCurrentMonth,
			skillReports,
			jobPostReports,
			topCandidatesPerJob
		).join();

		return new DashboardData(
			subscriptionStatus,
			totalCvs.join(),
			totalJobPosts.join(),
			totalUsers.join(),
			totalResumeMatches.join(),
			totalResumesProcessedInCurrentMonth.join(),
			skillReports.join(),
			jobPostReports.join(),
			topCandidatesPerJob.join()
		);
	}
}
