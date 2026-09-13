package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.repository.TenantRepository;
import ai.qorva.core.dto.common.SubscriptionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionSyncServiceTest {

	@Mock private TenantRepository tenantRepository;

	private static Tenant tenant(String subscriptionId) {
		var info = new SubscriptionInfo();
		info.setSubscriptionId(subscriptionId);
		info.setSubscriptionStatus("trialing");
		info.setCurrentPeriodStart(Instant.parse("2026-07-25T23:24:49Z"));
		info.setCurrentPeriodEnd(Instant.parse("2026-08-08T23:24:49Z"));
		var t = new Tenant();
		t.setId("t1");
		t.setSubscriptionInfo(info);
		return t;
	}

	@Test
	void writesStripeStateOntoTheTenant() {
		var snapshot = new SubscriptionSyncService.Snapshot("active",
			Instant.parse("2026-08-08T23:24:49Z"), Instant.parse("2027-08-08T23:24:49Z"), "year", "price_1", 99900L, false);
		var service = new SubscriptionSyncService(tenantRepository) {
			@Override protected Optional<Snapshot> fetch(String id) { return Optional.of(snapshot); }
		};
		var tenant = tenant("sub_1");

		var result = service.refreshFromStripe(tenant);

		assertThat(result).isPresent();
		var info = tenant.getSubscriptionInfo();
		assertThat(info.getSubscriptionStatus()).isEqualTo("active");
		assertThat(info.getCurrentPeriodStart()).isEqualTo(Instant.parse("2026-08-08T23:24:49Z"));
		assertThat(info.getCurrentPeriodEnd()).isEqualTo(Instant.parse("2027-08-08T23:24:49Z"));
		assertThat(info.getBillingCycle()).isEqualTo("year");
		assertThat(info.getPriceId()).isEqualTo("price_1");
		verify(tenantRepository).save(tenant);
	}

	@Test
	void leavesTheTenantAloneWithoutASubscriptionIdOrWhenStripeFails() {
		var failing = new SubscriptionSyncService(tenantRepository) {
			@Override protected Optional<Snapshot> fetch(String id) { return Optional.empty(); }
		};

		assertThat(failing.refreshFromStripe(tenant(null))).isEmpty();
		assertThat(failing.refreshFromStripe(tenant("sub_1"))).isEmpty();
		verify(tenantRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
