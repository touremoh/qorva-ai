package ai.qorva.core.service.help;

import ai.qorva.core.dao.entity.HelpConversation;
import ai.qorva.core.dao.entity.SupportTicket;
import ai.qorva.core.dao.repository.HelpConversationRepository;
import ai.qorva.core.dao.repository.SupportTicketRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.HelpData;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.utils.SupportedLanguages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Support requests from Qorva Help. Only ever created by the user confirming the form — the model can suggest one,
 * never send one. Stored first, then emailed to the support mailbox; a failed email is retried by
 * {@link #retryPendingEmails()}. Recipient and Reply-To come from configuration and the user's account, never
 * from the request.
 */
@Slf4j
@Service
public class SupportTicketService {

	static final int MAX_SUBJECT = 150;
	static final int MAX_DESCRIPTION = 4000;
	static final int MAX_EMAIL_ATTEMPTS = 5;
	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SupportTicketRepository tickets;
	private final HelpConversationRepository conversations;
	private final UserRepository users;
	private final TenantService tenantService;
	private final HelpRateLimiter rateLimiter;
	private final HelpAssistantService helpAssistant;
	private final ObjectProvider<SupportTicketEmailService> emailService;

	public SupportTicketService(SupportTicketRepository tickets, HelpConversationRepository conversations, UserRepository users,
	                            TenantService tenantService, HelpRateLimiter rateLimiter, HelpAssistantService helpAssistant,
	                            ObjectProvider<SupportTicketEmailService> emailService) {
		this.tickets = tickets;
		this.conversations = conversations;
		this.users = users;
		this.tenantService = tenantService;
		this.rateLimiter = rateLimiter;
		this.helpAssistant = helpAssistant;
		this.emailService = emailService;
	}

	public HelpData.TicketCreated create(String tenantId, String userEmail, String acceptLanguage, HelpData.TicketRequest request)
		throws QorvaException {
		helpAssistant.requireEnabled();
		var subject = oneLine(request != null ? request.subject() : null);
		var description = HelpAssistantService.sanitize(request != null ? request.description() : null);
		if (subject.isEmpty() || subject.length() > MAX_SUBJECT || description.isEmpty() || description.length() > MAX_DESCRIPTION) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.HELP_TICKET_INVALID);
		}
		rateLimiter.acquireTicket(userEmail);

		var ticket = new SupportTicket();
		ticket.setTenantId(tenantId);
		ticket.setUserEmail(userEmail);
		ticket.setUserName(userName(userEmail));
		ticket.setCompanyName(companyName(tenantId));
		ticket.setSubject(subject);
		ticket.setDescription(description);
		ticket.setPage(HelpLinks.isAllowed(request.page()) ? request.page() : null);
		ticket.setLanguage(SupportedLanguages.normalize(acceptLanguage));
		ticket.setTranscript(request.includeConversation() ? transcript(tenantId, userEmail, request.conversationId()) : new ArrayList<>());
		ticket.setStatus(SupportTicket.STATUS_OPEN);
		ticket.setEmailStatus(SupportTicket.EMAIL_PENDING);
		ticket.setCreatedAt(Instant.now());
		saveWithUniqueReference(ticket);
		sendEmail(ticket);
		return new HelpData.TicketCreated(ticket.getReference());
	}

	/** Emails that failed when the ticket was created, retried a few times (system scope: every tenant). */
	public int retryPendingEmails() {
		if (emailService.getIfAvailable() == null) return 0;
		var due = TenantScope.callAsSystem("support ticket email retry",
			// Older than the request that created them, so a retry never races the first send.
			() -> tickets.findTop50ByEmailStatusAndEmailAttemptsLessThanAndCreatedAtBeforeOrderByCreatedAtAsc(SupportTicket.EMAIL_PENDING,
				MAX_EMAIL_ATTEMPTS, Instant.now().minus(Duration.ofMinutes(2))));
		int sent = 0;
		for (var ticket : due) {
			if (TenantScope.callAs(ticket.getTenantId(), () -> sendEmail(ticket))) sent++;
		}
		return sent;
	}

	/** True when sent. A failure is recorded and left for the retry; the user already has their reference. */
	private boolean sendEmail(SupportTicket ticket) {
		var service = emailService.getIfAvailable();
		if (service == null) {
			log.warn("Support ticket {} stored, but notifications are disabled: not emailed", ticket.getReference());
			return false;
		}
		ticket.setEmailAttempts(ticket.getEmailAttempts() + 1);
		try {
			service.send(ticket);
			ticket.setEmailStatus(SupportTicket.EMAIL_SENT);
			ticket.setEmailedAt(Instant.now());
			return true;
		} catch (Exception e) {
			log.error("Support ticket {} could not be emailed (attempt {}): {}", ticket.getReference(), ticket.getEmailAttempts(),
				e.getMessage());
			if (ticket.getEmailAttempts() >= MAX_EMAIL_ATTEMPTS) ticket.setEmailStatus(SupportTicket.EMAIL_FAILED);
			return false;
		} finally {
			tickets.save(ticket);
		}
	}

	private void saveWithUniqueReference(SupportTicket ticket) {
		for (int attempt = 0; ; attempt++) {
			ticket.setReference(newReference());
			try {
				tickets.save(ticket);
				return;
			} catch (DuplicateKeyException e) {
				if (attempt >= 3) throw e;
			}
		}
	}

	/** Copied from the caller's own conversation only. */
	private List<HelpConversation.Turn> transcript(String tenantId, String userEmail, String conversationId) {
		if (conversationId == null || !conversationId.matches("[0-9a-fA-F]{24}")) return new ArrayList<>();
		return conversations.findByIdInTenant(conversationId, tenantId)
			.filter(c -> userEmail.equals(c.getUserEmail()))
			.map(c -> new ArrayList<>(c.getTurns()))
			.orElseGet(ArrayList::new);
	}

	private String userName(String userEmail) {
		var user = users.findByEmail(userEmail);
		if (user == null) return "";
		return ((user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();
	}

	private String companyName(String tenantId) {
		try {
			var tenant = tenantService.findOneById(tenantId);
			return tenant != null && tenant.getTenantName() != null ? tenant.getTenantName() : "";
		} catch (Exception e) {
			return "";
		}
	}

	static String newReference() {
		var sb = new StringBuilder("QH-");
		for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		return sb.toString();
	}

	/** One line: no CR/LF (it becomes an email subject), no control or invisible characters. */
	static String oneLine(String text) {
		return HelpAssistantService.sanitize(text).replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").strip();
	}
}
