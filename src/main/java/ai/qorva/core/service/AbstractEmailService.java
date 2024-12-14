package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import com.microsoft.graph.models.*;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.microsoft.kiota.authentication.AuthenticationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractEmailService {

	protected final OAuth2TokenService oauth2TokenService;
	protected static final String LEAD_NOTIF_TYPE = "LEAD";
	protected static final String ACC_NOTIF_TYPE = "ACC";

	@Value("${spring.mail.username}")
	protected String senderEmail;

	@Value("${spring.mail.from}")
	protected String fromEmail;

	protected AbstractEmailService(OAuth2TokenService oauth2TokenService) {
		this.oauth2TokenService = oauth2TokenService;
	}


	public void sendEmail(String receiverEmail, String subject, String content) throws QorvaException {
		try {
			var recipient = new Recipient();
			var emailAddress = new EmailAddress();
			emailAddress.setAddress(receiverEmail);
			recipient.setEmailAddress(emailAddress);

			var message = new Message();
			message.setToRecipients(List.of(recipient));
			message.setSubject(subject);


			var itemBody = new ItemBody();
			itemBody.setContent(content);
			itemBody.setContentType(BodyType.Html);
			message.setBody(itemBody);


			// Specify the "From" address (shared mailbox)
			EmailAddress fromAddress = new EmailAddress();
			fromAddress.setAddress(this.fromEmail);  // Shared mailbox address
			message.setFrom(new Recipient());
			Objects.requireNonNull(message.getFrom()).setEmailAddress(fromAddress);

			var accessToken = this.oauth2TokenService.getAccessToken();

			AuthenticationProvider authProvider = (request, _) -> request.headers.put("Authorization", Set.of("Bearer " + accessToken));
			var graphClient = new GraphServiceClient(authProvider);

			var postRequest = new SendMailPostRequestBody();
			postRequest.setMessage(message);

			graphClient
				.users()
				.byUserId(this.senderEmail)
				.sendMail()
				.post(postRequest);
		} catch (MailException e) {
			log.error("Failed to send email to user {}. Error message is: {}", receiverEmail, e.getMessage(), e);
			throw new QorvaException(
				"Failed to send email to user",
				e,
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR
			);
		}
	}

	protected String loadHtmlTemplate(String filePath) throws IOException {
		ClassPathResource resource = new ClassPathResource(filePath);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining(System.lineSeparator()));
		}
	}
}
