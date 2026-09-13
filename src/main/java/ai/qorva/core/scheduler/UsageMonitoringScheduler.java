package ai.qorva.core.scheduler;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.repository.TenantRepository;
import ai.qorva.core.dto.common.FeatureLimits;
import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import ai.qorva.core.service.SubscriptionSyncService;
import ai.qorva.core.service.UsageMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class UsageMonitoringScheduler {

    private final TenantRepository tenantRepository;
    private final UsageMonitoringService usageMonitoringService;
    private final ProductReferenceService productReferenceService;
    private final SubscriptionSyncService subscriptionSyncService;
    private final Set<String> warnedTenants = ConcurrentHashMap.newKeySet();

    @Autowired
    public UsageMonitoringScheduler(
        TenantRepository tenantRepository,
        UsageMonitoringService usageMonitoringService,
        ProductReferenceService productReferenceService,
        SubscriptionSyncService subscriptionSyncService
    ) {
        this.tenantRepository = tenantRepository;
        this.usageMonitoringService = usageMonitoringService;
        this.productReferenceService = productReferenceService;
        this.subscriptionSyncService = subscriptionSyncService;
    }

    @Scheduled(cron = "0 0/5 * * * *")
    public void initializeUsageMonitoringPeriods() {
        log.debug("Usage monitoring check: initializing usage monitoring periods");
        var statuses = List.of(
            SubscriptionStatus.ACTIVE.getValue(),
            SubscriptionStatus.TRIALING.getValue()
        );

        var tenants = tenantRepository.findAllBySubscriptionStatusIn(statuses);
        log.debug("Usage monitoring check: {} active/trialing tenant(s) found", tenants.size());

        int initialized = 0;
        int skipped = 0;
        int failed = 0;

        for (var tenant : tenants) {
            try {
                if (processForTenant(tenant)) {
                    initialized++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to process usage monitoring for tenant={}", tenant.getId(), e);
            }
        }

        log.debug("Usage monitoring check complete: initialized={} skipped={} failed={}", initialized, skipped, failed);
    }

    /**
     * Returns true if a new period was initialized, false if the tenant already has a valid period.
     */
    private boolean processForTenant(Tenant tenant) throws QorvaException {
        // findCurrentPeriodByTenantId returns empty for both "no document" and "expired period"
        // (its query filters currentPeriodStart <= now < currentPeriodEnd)
        if (usageMonitoringService.findCurrentPeriodByTenantId(tenant.getId()).isPresent()) {
            warnedTenants.remove(tenant.getId());
            log.debug("Tenant {} already has an active usage period — skipping", tenant.getId());
            return false;
        }

        var now = Instant.now();
        var sub = tenant.getSubscriptionInfo();

        // The dates on the tenant are only as fresh as the last webhook. When the period they
        // describe is over (or missing), ask Stripe instead of inserting — every 5 minutes — a
        // period that is expired on arrival.
        if (sub == null || sub.getCurrentPeriodEnd() == null || !sub.getCurrentPeriodEnd().isAfter(now)) {
            var refreshed = subscriptionSyncService.refreshFromStripe(tenant);
            if (refreshed.isEmpty()) {
                warnOnce(tenant.getId(), "period on record is over or missing and Stripe could not be consulted");
                return false;
            }
            sub = refreshed.get();
            if (!List.of(SubscriptionStatus.ACTIVE.getValue(), SubscriptionStatus.TRIALING.getValue()).contains(sub.getSubscriptionStatus())) {
                log.info("Tenant {} subscription is {} according to Stripe — no usage period", tenant.getId(), sub.getSubscriptionStatus());
                return false;
            }
        }
        if (sub.getCurrentPeriodStart() == null || sub.getCurrentPeriodEnd() == null) {
            warnOnce(tenant.getId(), "no currentPeriodStart/End in subscriptionInfo");
            return false;
        }
        if (!sub.getCurrentPeriodEnd().isAfter(now)) {
            warnOnce(tenant.getId(), "Stripe period " + sub.getCurrentPeriodStart() + " → " + sub.getCurrentPeriodEnd() + " is already over");
            return false;
        }

        ProductFeatures features = null;
        if (StringUtils.hasText(sub.getPriceId())) {
            var product = productReferenceService.findByStripePriceId(sub.getPriceId());
            features = product != null ? product.getFeatures() : null;
            if (features == null) {
                log.warn("No product features found for priceId={}, tenant={} — period will have null limits",
                    sub.getPriceId(), tenant.getId());
            }
        }

        int cycleMultiplier = "year".equalsIgnoreCase(sub.getBillingCycle()) ? 12 : 1;
        if (cycleMultiplier > 1) {
            features = scaleFeatures(features, cycleMultiplier);
        }

        usageMonitoringService.initializePeriod(
            tenant.getId(),
            sub.getSubscriptionPlan(),
            sub.getCurrentPeriodStart(),
            sub.getCurrentPeriodEnd(),
            features
        );
        warnedTenants.remove(tenant.getId());
        return true;
    }

    /** The scheduler runs every 5 minutes; a tenant that cannot be metered is reported once, not 288 times a day. */
    private void warnOnce(String tenantId, String reason) {
        if (warnedTenants.add(tenantId)) {
            log.warn("Tenant {} cannot get a usage monitoring period: {} — skipping until it changes", tenantId, reason);
        } else {
            log.debug("Tenant {} still cannot get a usage monitoring period: {}", tenantId, reason);
        }
    }

    private ProductFeatures scaleFeatures(ProductFeatures source, int multiplier) {
        if (source == null || source.getLimits() == null) {
            return source;
        }
        var base = source.getLimits();
        var scaledLimits = FeatureLimits.builder()
            .screeningActions(base.getScreeningActions() != null ? base.getScreeningActions() * multiplier : null)
            .aiResumeChats(base.getAiResumeChats() != null ? base.getAiResumeChats() * multiplier : null)
            .talentIntelligenceQueries(base.getTalentIntelligenceQueries() != null ? base.getTalentIntelligenceQueries() * multiplier : null)
            // Static caps, not monthly consumption — never multiplied by billing cycle.
            .emailTemplates(base.getEmailTemplates())
            .bulkUploadFiles(base.getBulkUploadFiles())
            .atsConnections(base.getAtsConnections())
            .build();
        return ProductFeatures.builder()
            .seats(source.getSeats())
            .limits(scaledLimits)
            .overage(source.getOverage())
            .build();
    }
}
