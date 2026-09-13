package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.repository.TenantRepository;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.utils.SubscriptionStatusHelper;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Decimal128;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

/**
 * Pulls a tenant's subscription state from Stripe and writes it onto the tenant. Webhooks are
 * the normal path; this is the fallback for when one was missed (a local backend never
 * receives them, a deploy was down, …) and the period on record has gone stale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSyncService {

	private final TenantRepository tenantRepository;

	/** What the scheduler needs from Stripe; a seam so tests do not hit the API. */
	public record Snapshot(String status, Instant periodStart, Instant periodEnd, String billingCycle,
	                       String priceId, Long amount, Boolean cancelAtPeriodEnd) {}

	/**
	 * Refreshes status, period and price from Stripe. Returns the updated info, or empty when the
	 * tenant has no subscription id or Stripe could not be reached (the caller keeps what it had).
	 */
	public Optional<SubscriptionInfo> refreshFromStripe(Tenant tenant) {
		var info = tenant.getSubscriptionInfo();
		if (info == null || !StringUtils.hasText(info.getSubscriptionId())) {
			return Optional.empty();
		}
		Optional<Snapshot> snapshot = fetch(info.getSubscriptionId());
		if (snapshot.isEmpty()) {
			return Optional.empty();
		}
		apply(info, snapshot.get());
		tenantRepository.save(tenant);
		log.info("Subscription re-synced from Stripe for tenant={} status={} period={} → {}",
			tenant.getId(), info.getSubscriptionStatus(), info.getCurrentPeriodStart(), info.getCurrentPeriodEnd());
		return Optional.of(info);
	}

	protected Optional<Snapshot> fetch(String subscriptionId) {
		try {
			Subscription sub = Subscription.retrieve(subscriptionId);
			SubscriptionItem item = sub.getItems().getData().getFirst();
			return Optional.of(new Snapshot(
				SubscriptionStatusHelper.subscriptionFromCode(sub.getStatus()),
				item.getCurrentPeriodStart() != null ? Instant.ofEpochSecond(item.getCurrentPeriodStart()) : null,
				item.getCurrentPeriodEnd() != null ? Instant.ofEpochSecond(item.getCurrentPeriodEnd()) : null,
				item.getPlan() != null ? item.getPlan().getInterval() : null,
				item.getPrice() != null ? item.getPrice().getId() : null,
				item.getPlan() != null ? item.getPlan().getAmount() : null,
				sub.getCancelAtPeriodEnd()));
		} catch (StripeException e) {
			log.warn("Could not retrieve subscription {} from Stripe: {}", subscriptionId, e.getMessage());
			return Optional.empty();
		}
	}

	static void apply(SubscriptionInfo info, Snapshot s) {
		info.setSubscriptionStatus(s.status());
		info.setCurrentPeriodStart(s.periodStart());
		info.setCurrentPeriodEnd(s.periodEnd());
		if (s.billingCycle() != null) info.setBillingCycle(s.billingCycle());
		if (s.priceId() != null) { info.setPriceId(s.priceId()); info.setPlanCode(s.priceId()); }
		if (s.amount() != null) info.setPrice(new Decimal128(s.amount()));
		info.setCancelAtPeriodEnd(s.cancelAtPeriodEnd());
	}
}
