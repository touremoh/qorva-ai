package ai.qorva.core.scheduler;

import ai.qorva.core.service.TenantAccess;
import ai.qorva.core.security.TenantScope;

import ai.qorva.core.service.EmailNotificationDispatcher;
import ai.qorva.core.service.PendingEmailNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "qorva.notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PendingEmailNotificationScheduler {

    private final PendingEmailNotificationService pendingEmailService;
    private final EmailNotificationDispatcher dispatcher;
    private final TenantAccess tenantAccess;

    @Autowired
    public PendingEmailNotificationScheduler(
        PendingEmailNotificationService pendingEmailService,
        EmailNotificationDispatcher dispatcher,
        TenantAccess tenantAccess
    ) {
        this.tenantAccess = tenantAccess;
        this.pendingEmailService = pendingEmailService;
        this.dispatcher = dispatcher;
    }

    @Scheduled(cron = "0 * * * * *")
    public void processPendingNotifications() {
        var pending = pendingEmailService.findPending();
        if (pending.isEmpty()) {
            return;
        }

        log.info("PendingEmailNotification check: {} pending record(s) to process", pending.size());
        int sent = 0;
        int failed = 0;

        for (var notification : pending) {
            if (!tenantAccess.isUsable(notification.getTenantId())) {
                // Held (still PENDING) while the company is suspended or expired; a purge deletes them.
                continue;
            }
            try {
                // Sent and marked sent in the notification's own tenant: the status update reads the notification
                // through the tenant-scoped service, like the send.
                TenantScope.runAs(notification.getTenantId(), () -> {
                    dispatcher.dispatch(notification);
                    pendingEmailService.markSent(notification.getId());
                });
                sent++;
                log.info("Notification dispatched: id={} type={} userId={}",
                    notification.getId(), notification.getNotificationType(), notification.getUserId());
            } catch (Exception e) {
                failed++;
                log.error("Failed to dispatch notification: id={} type={} attempt={}/{}",
                    notification.getId(), notification.getNotificationType(),
                    notification.getAttempts() + 1, notification.getMaxAttempts(), e);
                try {
                    TenantScope.runAs(notification.getTenantId(),
                        () -> pendingEmailService.markFailed(notification.getId(), e.getMessage()));
                } catch (Exception markEx) {
                    log.error("Failed to mark notification as failed: id={}", notification.getId(), markEx);
                }
            }
        }

        log.info("PendingEmailNotification check complete: sent={} failed={}", sent, failed);
    }
}
