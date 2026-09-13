package ai.qorva.core.scheduler;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.repository.TenantRepository;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.service.ProductReferenceService;
import ai.qorva.core.service.SubscriptionSyncService;
import ai.qorva.core.service.UsageMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageMonitoringSchedulerTest {

	@Mock private TenantRepository tenantRepository;
	@Mock private UsageMonitoringService usageMonitoringService;
	@Mock private ProductReferenceService productReferenceService;
	@Mock private SubscriptionSyncService subscriptionSyncService;

	private UsageMonitoringScheduler scheduler;
	private Tenant tenant;

	@BeforeEach
	void setUp() {
		scheduler = new UsageMonitoringScheduler(tenantRepository, usageMonitoringService, productReferenceService, subscriptionSyncService);
		tenant = tenant(daysAgo(50), daysAgo(36)); // the 14-day trial on record is long over
		when(tenantRepository.findAllBySubscriptionStatusIn(anyList())).thenReturn(List.of(tenant));
		when(usageMonitoringService.findCurrentPeriodByTenantId("t1")).thenReturn(Optional.empty());
	}

	private static Instant daysAgo(int d) { return Instant.now().minus(d, ChronoUnit.DAYS); }

	private static Tenant tenant(Instant start, Instant end) {
		var sub = new SubscriptionInfo();
		sub.setSubscriptionId("sub_1");
		sub.setSubscriptionStatus("trialing");
		sub.setSubscriptionPlan("Starter");
		sub.setBillingCycle("year");
		sub.setCurrentPeriodStart(start);
		sub.setCurrentPeriodEnd(end);
		var t = new Tenant();
		t.setId("t1");
		t.setSubscriptionInfo(sub);
		return t;
	}

	private static SubscriptionInfo synced(String status, Instant start, Instant end) {
		var s = new SubscriptionInfo();
		s.setSubscriptionStatus(status);
		s.setSubscriptionPlan("Starter");
		s.setBillingCycle("year");
		s.setCurrentPeriodStart(start);
		s.setCurrentPeriodEnd(end);
		return s;
	}

	@Test
	void aStalePeriodIsResyncedFromStripeAndTheFreshPeriodIsOpened() throws Exception {
		Instant start = daysAgo(36), end = start.plus(365, ChronoUnit.DAYS);
		when(subscriptionSyncService.refreshFromStripe(tenant)).thenReturn(Optional.of(synced("active", start, end)));

		scheduler.initializeUsageMonitoringPeriods();

		verify(usageMonitoringService).initializePeriod(eq("t1"), eq("Starter"), eq(start), eq(end), any());
	}

	@Test
	void aSubscriptionStripeReportsAsCanceledGetsNoPeriod() throws Exception {
		when(subscriptionSyncService.refreshFromStripe(tenant)).thenReturn(Optional.of(synced("canceled", daysAgo(36), daysAgo(6))));

		scheduler.initializeUsageMonitoringPeriods();

		verify(usageMonitoringService, never()).initializePeriod(any(), any(), any(), any(), any());
	}

	@Test
	void anExpiredPeriodIsNeverInsertedWhenStripeCannotBeConsulted() throws Exception {
		when(subscriptionSyncService.refreshFromStripe(tenant)).thenReturn(Optional.empty());

		scheduler.initializeUsageMonitoringPeriods();
		scheduler.initializeUsageMonitoringPeriods(); // second run: warned once already, still no insert

		verify(usageMonitoringService, never()).initializePeriod(any(), any(), any(), any(), any());
		verify(subscriptionSyncService, times(2)).refreshFromStripe(tenant);
	}

	@Test
	void aPeriodStillCoveringNowIsOpenedWithoutAskingStripe() throws Exception {
		Instant start = daysAgo(3), end = start.plus(30, ChronoUnit.DAYS);
		tenant.getSubscriptionInfo().setCurrentPeriodStart(start);
		tenant.getSubscriptionInfo().setCurrentPeriodEnd(end);

		scheduler.initializeUsageMonitoringPeriods();

		verify(subscriptionSyncService, never()).refreshFromStripe(any());
		verify(usageMonitoringService).initializePeriod(eq("t1"), eq("Starter"), eq(start), eq(end), any());
	}
}
