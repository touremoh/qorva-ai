package ai.qorva.core.scheduler;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.repository.TenantRepository;
import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import ai.qorva.core.service.UsageMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
public class UsageMonitoringScheduler {

    private final TenantRepository tenantRepository;
    private final UsageMonitoringService usageMonitoringService;
    private final ProductReferenceService productReferenceService;

    @Autowired
    public UsageMonitoringScheduler(
        TenantRepository tenantRepository,
        UsageMonitoringService usageMonitoringService,
        ProductReferenceService productReferenceService
    ) {
        this.tenantRepository = tenantRepository;
        this.usageMonitoringService = usageMonitoringService;
        this.productReferenceService = productReferenceService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void initializeUsageMonitoringPeriods() {
        var statuses = List.of(
            SubscriptionStatus.ACTIVE.getValue(),
            SubscriptionStatus.TRIALING.getValue()
        );

        var tenants = tenantRepository.findAllBySubscriptionStatusIn(statuses);
        log.info("Usage monitoring check: {} active/trialing tenant(s) found", tenants.size());

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

        log.info("Usage monitoring check complete: initialized={} skipped={} failed={}", initialized, skipped, failed);
    }

    /**
     * Returns true if a new period was initialized, false if the tenant already has a valid period.
     */
    private boolean processForTenant(Tenant tenant) throws QorvaException {
        // findCurrentPeriodByTenantId returns empty for both "no document" and "expired period"
        // (its query filters currentPeriodStart <= now < currentPeriodEnd)
        if (usageMonitoringService.findCurrentPeriodByTenantId(tenant.getId()).isPresent()) {
            log.debug("Tenant {} already has an active usage period — skipping", tenant.getId());
            return false;
        }

        var sub = tenant.getSubscriptionInfo();
        if (sub == null || sub.getCurrentPeriodStart() == null || sub.getCurrentPeriodEnd() == null) {
            log.warn("Tenant {} has no currentPeriodStart/End in subscriptionInfo — skipping", tenant.getId());
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

        usageMonitoringService.initializePeriod(
            tenant.getId(),
            sub.getSubscriptionPlan(),
            sub.getCurrentPeriodStart(),
            sub.getCurrentPeriodEnd(),
            features
        );
        return true;
    }
}
