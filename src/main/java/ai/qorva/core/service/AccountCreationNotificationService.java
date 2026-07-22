package ai.qorva.core.service;

import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.EmailTitlesEnum;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "qorva.notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AccountCreationNotificationService extends AbstractEmailService implements QorvaNotificationService {

	@Value("${qorva.assets.logo-url}")
	private String logoUrl;

	@Autowired
	public AccountCreationNotificationService(OAuth2TokenService oauth2TokenService) {
		super(oauth2TokenService);
	}

	@Override
	public void send(UserDTO receiver, String languageCode) throws QorvaException {
		try {
			String htmlTemplate = loadHtmlTemplate("templates/emails/account-activation-template.html");
			String templateContent = loadHtmlTemplate("templates/emails/" + languageCode + "_notification_content.html");

			templateContent = templateContent
				.replace("{{current_year}}", String.valueOf(LocalDate.now().getYear()))
				.replace("{{first_name}}", receiver.getFirstName())
				.replace("{{app_name}}", "Qorva AI")
				.replace("{{user_email}}", receiver.getEmail())
				.replace("{{support_email}}", this.fromEmail);

			String htmlContent = htmlTemplate.replace("{{template_content}}", templateContent);
			sendEmail(receiver.getEmail(), EmailTitlesEnum.getEmailTitle(languageCode), htmlContent);
		} catch (IOException | MailException e) {
			log.error("Failed to send account creation email to {}", receiver.getEmail(), e);
			throw new QorvaException(
				"Failed to send notification to user",
				e,
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR
			);
		}
	}

	public void sendDemoWelcome(UserDTO receiver, Map<String, String> payload, String languageCode) throws QorvaException {
		String lang = languageCode != null ? languageCode : "en";
		try {
			String wrapper = loadHtmlTemplate("templates/emails/user-added-template.html");
			String content = loadHtmlTemplate("templates/emails/" + lang + "_demo_welcome_content.html");

			String setPasswordUrl = payload != null ? payload.getOrDefault("setPasswordUrl", "") : "";
			String companyName = payload != null ? payload.getOrDefault("companyName", "") : "";

			content = content
				.replace("{{logo_url}}", logoUrl)
				.replace("{{first_name}}", receiver.getFirstName())
				.replace("{{app_name}}", "Qorva AI")
				.replace("{{company_name}}", companyName)
				.replace("{{user_email}}", receiver.getEmail())
				.replace("{{set_password_url}}", setPasswordUrl)
				.replace("{{support_email}}", this.fromEmail)
				.replace("{{current_year}}", String.valueOf(LocalDate.now().getYear()));

			String html = wrapper.replace("{{template_content}}", content);
			sendEmail(receiver.getEmail(), EmailTitlesEnum.getEmailTitle(lang, "demo_welcome"), html);
			log.info("Demo-welcome email sent to email={}", receiver.getEmail());
		} catch (IOException | MailException e) {
			log.error("Failed to send demo-welcome email to {}", receiver.getEmail(), e);
			throw new QorvaException(
				"Failed to send demo-welcome notification",
				e,
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR
			);
		}
	}

	public void sendUserAdded(UserDTO receiver, Map<String, String> payload, String languageCode) throws QorvaException {
		String lang = languageCode != null ? languageCode : "en";
		try {
			String wrapper = loadHtmlTemplate("templates/emails/user-added-template.html");
			String content = loadHtmlTemplate("templates/emails/" + lang + "_user_added_content.html");

			String tempPassword = payload != null ? payload.getOrDefault("temporaryPassword", "") : "";
			String companyName = payload != null ? payload.getOrDefault("companyName", "") : "";

			content = content
				.replace("{{logo_url}}", logoUrl)
				.replace("{{first_name}}", receiver.getFirstName())
				.replace("{{app_name}}", "Qorva AI")
				.replace("{{company_name}}", companyName)
				.replace("{{user_email}}", receiver.getEmail())
				.replace("{{temporary_password}}", tempPassword)
				.replace("{{support_email}}", this.fromEmail)
				.replace("{{current_year}}", String.valueOf(LocalDate.now().getYear()));

			String html = wrapper.replace("{{template_content}}", content);
			sendEmail(receiver.getEmail(), EmailTitlesEnum.getEmailTitle(lang, "user_added"), html);
			log.info("User-added email sent to email={}", receiver.getEmail());
		} catch (IOException | MailException e) {
			log.error("Failed to send user-added email to {}", receiver.getEmail(), e);
			throw new QorvaException(
				"Failed to send user-added notification",
				e,
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR
			);
		}
	}
}
