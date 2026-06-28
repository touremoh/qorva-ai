package ai.qorva.core.scheduler;

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

    @Autowired
    public PendingEmailNotificationScheduler(
        PendingEmailNotificationService pendingEmailService,
        EmailNotificationDispatcher dispatcher
    ) {
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
            try {
                dispatcher.dispatch(notification);
                pendingEmailService.markSent(notification.getId());
                sent++;
                log.info("Notification dispatched: id={} type={} userId={}",
                    notification.getId(), notification.getNotificationType(), notification.getUserId());
            } catch (Exception e) {
                failed++;
                log.error("Failed to dispatch notification: id={} type={} attempt={}/{}",
                    notification.getId(), notification.getNotificationType(),
                    notification.getAttempts() + 1, notification.getMaxAttempts(), e);
                try {
                    pendingEmailService.markFailed(notification.getId(), e.getMessage());
                } catch (Exception markEx) {
                    log.error("Failed to mark notification as failed: id={}", notification.getId(), markEx);
                }
            }
        }

        log.info("PendingEmailNotification check complete: sent={} failed={}", sent, failed);
    }
}
