package ai.qorva.core.admin.service;

import ai.qorva.core.dao.entity.PlatformAdmin;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.EmailCategory;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.AbstractEmailService;
import ai.qorva.core.service.EmailSenderResolver;
import ai.qorva.core.service.OAuth2TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Emails to admins: sign-in codes and set-password invitations. Sent synchronously (never through the pending-email
 * queue, which would store the code or link). English only, like the console.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "qorva.notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdminNotificationService extends AbstractEmailService {

	private static final String WRAPPER = "templates/emails/user-added-template.html";

	@Value("${qorva.assets.logo-url}")
	private String logoUrl;

	public AdminNotificationService(OAuth2TokenService oauth2TokenService, EmailSenderResolver senderResolver) {
		super(oauth2TokenService, senderResolver);
	}

	@Override
	protected EmailCategory getCategory() {
		return EmailCategory.SECURITY;
	}

	@Override
	public void send(UserDTO user, String languageCode) {
		throw new UnsupportedOperationException("Use sendCode or sendInvite");
	}

	public void sendCode(PlatformAdmin admin, String code, long ttlMinutes) throws QorvaException {
		render(admin, "templates/emails/en_mfa_code_content.html", "Your Qorva admin sign-in code", content -> content
			.replace("{{intro_line}}", "Use this code to finish signing in to the <strong>Qorva admin console</strong>.")
			.replace("{{code}}", code)
			.replace("{{ttl_minutes}}", String.valueOf(ttlMinutes))
			.replace("{{app_name}}", "Qorva AI"));
	}

	public void sendInvite(PlatformAdmin admin, String link, long ttlHours) throws QorvaException {
		render(admin, "templates/emails/en_admin_invite_content.html", "Your access to the Qorva admin console", content -> content
			.replace("{{role}}", admin.getRole())
			.replace("{{link}}", link)
			.replace("{{ttl_hours}}", String.valueOf(ttlHours)));
	}

	private void render(PlatformAdmin admin, String template, String subject,
	                    java.util.function.UnaryOperator<String> fill) throws QorvaException {
		try {
			var content = fill.apply(loadHtmlTemplate(template))
				.replace("{{logo_url}}", logoUrl)
				.replace("{{first_name}}", Objects.requireNonNullElse(admin.getFirstName(), ""))
				.replace("{{support_email}}", supportEmail())
				.replace("{{current_year}}", String.valueOf(LocalDate.now().getYear()));
			sendEmail(admin.getEmail(), subject, loadHtmlTemplate(WRAPPER).replace("{{template_content}}", content));
			log.info("Admin email sent: adminId={} subject='{}'", admin.getId(), subject);
		} catch (IOException | RuntimeException e) {
			log.error("Failed to send admin email: adminId={}", admin.getId(), e);
			throw new QorvaException("Failed to send admin email", e,
				HttpStatus.SERVICE_UNAVAILABLE.value(), HttpStatus.SERVICE_UNAVAILABLE);
		}
	}
}
