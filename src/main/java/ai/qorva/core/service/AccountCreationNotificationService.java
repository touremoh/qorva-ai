package ai.qorva.core.service;

import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.EmailTitlesEnum;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;

@Slf4j
@Service
public class AccountCreationNotificationService extends AbstractEmailService {

	@Autowired
	public AccountCreationNotificationService(OAuth2TokenService oauth2TokenService) {
		super(oauth2TokenService);
	}

	public void sendNotification(UserDTO receiver, String languageCode) throws QorvaException {
		try {
			// Load the HTML template from file
			String htmlTemplate = loadHtmlTemplate("templates/account-activation-template.html");

			// Get the template content
			String templateContent = loadHtmlTemplate("templates/"+languageCode+"_notification_content.html");

			// Replace placeholders with dynamic content
			templateContent = templateContent
				.replace("{{current_year}}", String.valueOf(LocalDate.now().getYear()))
				.replace("{{first_name}}", receiver.getFirstName())
				.replace("{{app_name}}", "Qorva AI")
				.replace("{{user_email}}", receiver.getEmail())
				.replace("{{support_email}}", this.fromEmail)
			;

			// Update html template
			String htmlContent = htmlTemplate.replace("{{template_content}}", templateContent);

			// Send Email
			sendEmail(receiver.getEmail(), EmailTitlesEnum.getEmailTitle(languageCode), htmlContent);
		} catch (IOException | MailException e) {
			log.error("Failed to send email notification to the user {}. Error message is: {}", receiver.getEmail(), e.getMessage(), e);
			throw new QorvaException(
				"Failed to send notification to user",
				e,
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR
			);
		}
	}
}
