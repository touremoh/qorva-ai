package ai.qorva.core.scheduler;

import ai.qorva.core.service.help.SupportTicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries support-ticket emails that failed when the ticket was created (up to five attempts). */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "qorva.notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SupportTicketEmailScheduler {

	private final SupportTicketService supportTicketService;

	public SupportTicketEmailScheduler(SupportTicketService supportTicketService) {
		this.supportTicketService = supportTicketService;
	}

	@Scheduled(cron = "0 */10 * * * *")
	public void retryPendingEmails() {
		try {
			int sent = supportTicketService.retryPendingEmails();
			if (sent > 0) log.info("Support ticket emails retried: {} sent", sent);
		} catch (Exception e) {
			log.error("Support ticket email retry failed", e);
		}
	}
}
