package ai.qorva.core.service.help;

import ai.qorva.core.dao.entity.SupportTicket;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.EmailCategory;
import ai.qorva.core.enums.EmailTitlesEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.AbstractEmailService;
import ai.qorva.core.service.EmailSenderResolver;
import ai.qorva.core.service.OAuth2TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Sends a support request to the support mailbox, with Reply-To set to the user's account email so support answers
 * them directly. English only (internal). Every value written by the user is HTML-escaped.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "qorva.notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SupportTicketEmailService extends AbstractEmailService {

	private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

	public SupportTicketEmailService(OAuth2TokenService oauth2TokenService, EmailSenderResolver senderResolver) {
		super(oauth2TokenService, senderResolver);
	}

	@Override
	protected EmailCategory getCategory() {
		return EmailCategory.SUPPORT;
	}

	/** Not sent to a user; use {@link #send(SupportTicket)}. */
	@Override
	public void send(UserDTO user, String languageCode) {
		throw new UnsupportedOperationException("Support tickets are sent with send(SupportTicket)");
	}

	public void send(SupportTicket ticket) throws QorvaException {
		var to = supportEmail();
		if (to == null || to.isBlank()) {
			throw new QorvaException("No support mailbox configured", HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR);
		}
		try {
			var wrapper = loadHtmlTemplate("templates/emails/user-added-template.html");
			var content = loadHtmlTemplate("templates/emails/en_support_ticket_content.html")
				.replace("{{reference}}", esc(ticket.getReference()))
				.replace("{{user_name}}", esc(ticket.getUserName()))
				.replace("{{user_email}}", esc(ticket.getUserEmail()))
				.replace("{{company_name}}", esc(ticket.getCompanyName()))
				.replace("{{tenant_id}}", esc(ticket.getTenantId()))
				.replace("{{page}}", esc(ticket.getPage() != null ? ticket.getPage() : "—"))
				.replace("{{language}}", esc(ticket.getLanguage()))
				.replace("{{created_at}}", ticket.getCreatedAt() != null ? CREATED.format(ticket.getCreatedAt()) : "")
				.replace("{{subject}}", esc(ticket.getSubject()))
				.replace("{{description}}", esc(ticket.getDescription()))
				.replace("{{transcript}}", transcript(ticket))
				.replace("{{app_name}}", "Qorva AI")
				.replace("{{current_year}}", String.valueOf(LocalDate.now().getYear()));
			var subject = EmailTitlesEnum.getEmailTitle("en", "support_ticket") + " " + ticket.getReference() + ": " + ticket.getSubject();
			sendEmail(to, subject, wrapper.replace("{{template_content}}", content), ticket.getUserEmail());
			log.info("Support ticket {} emailed to support", ticket.getReference());
		} catch (IOException e) {
			throw new QorvaException("Failed to build the support ticket email", e, HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	static String transcript(SupportTicket ticket) {
		if (ticket.getTranscript() == null || ticket.getTranscript().isEmpty()) return "";
		var sb = new StringBuilder("<p style=\"font-weight: 700; margin: 24px 0 6px;\">Qorva Help conversation</p>");
		for (var turn : ticket.getTranscript()) {
			sb.append("<p style=\"margin: 10px 0 2px; color: #64748B; font-size: 13px;\">User</p>")
				.append("<div style=\"white-space: pre-wrap; font-size: 14px;\">").append(esc(turn.getQuestion())).append("</div>")
				.append("<p style=\"margin: 10px 0 2px; color: #64748B; font-size: 13px;\">Qorva Help</p>")
				.append("<div style=\"white-space: pre-wrap; font-size: 14px;\">").append(esc(turn.getAnswer())).append("</div>");
		}
		return sb.toString();
	}

	private static String esc(String value) {
		return value == null ? "" : HtmlUtils.htmlEscape(value);
	}
}
