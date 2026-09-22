package ai.qorva.core.service;

import ai.qorva.core.dto.DashboardData;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

	private static final String TENANT_ID = "tenant-1";

	@Mock private UserService userService;
	@Mock private MatchingReportService matchingReportService;
	@Mock private JobPostService jobPostService;
	@Mock private CVService cvService;
	@Mock private TenantService tenantService;
	@Mock private UsageMonitoringService usageMonitoringService;

	private ExecutorService executor;
	private DashboardService dashboardService;
	private final UserDetails principal = User.withUsername("recruiter@example.com").password("x").build();

	@BeforeEach
	void setUp() throws QorvaException {
		executor = Executors.newVirtualThreadPerTaskExecutor();
		dashboardService = new DashboardService(userService, matchingReportService, jobPostService, cvService, tenantService, usageMonitoringService, executor);

		when(userService.findOneByCriteria(any())).thenReturn(UserDTO.builder().tenantId(TENANT_ID).build());
		var subscriptionInfo = new SubscriptionInfo();
		subscriptionInfo.setSubscriptionStatus("ACTIVE");
		when(tenantService.findOneById(TENANT_ID)).thenReturn(TenantDTO.builder().subscriptionInfo(subscriptionInfo).build());
	}

	@AfterEach
	void tearDown() {
		executor.close();
	}

	@Test
	void failingReportFallsBackWithoutFailingTheDashboard() throws QorvaException {
		when(cvService.getSkillReportByTenantId(TENANT_ID)).thenThrow(new IllegalStateException("aggregation failed"));
		var jobs = List.of(new DashboardData.ApplicationPerJobPostReport("job-1", "Java Developer", 3));
		when(matchingReportService.getApplicationsPerJobPost(TENANT_ID)).thenReturn(jobs);
		when(cvService.countAll(TENANT_ID)).thenReturn(42L);

		var data = dashboardService.getDashboardData(principal);

		assertThat(data.skillsReport()).isEmpty();
		assertThat(data.jobPostsReport()).isEqualTo(jobs);
		assertThat(data.totalCVs()).isEqualTo(42L);
		assertThat(data.subscriptionStatus()).isEqualTo("ACTIVE");
	}

	@Test
	void failingCountFallsBackToZero() throws QorvaException {
		when(cvService.countAll(TENANT_ID)).thenThrow(new QorvaException("count failed"));
		when(jobPostService.countAll(TENANT_ID)).thenReturn(7L);

		var data = dashboardService.getDashboardData(principal);

		assertThat(data.totalCVs()).isZero();
		assertThat(data.totalJobsPosted()).isEqualTo(7L);
	}
}
