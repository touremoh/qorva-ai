package ai.qorva.core.service;

import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.EmailCategory;
import ai.qorva.core.exception.QorvaException;
import com.microsoft.graph.models.*;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.microsoft.kiota.authentication.AuthenticationProvider;
import lombok.extern.slf4j.Slf4j;
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
	protected final EmailSenderResolver senderResolver;
	protected static final String LEAD_NOTIF_TYPE = "LEAD";
	protected static final String ACC_NOTIF_TYPE = "ACC";

	protected AbstractEmailService(OAuth2TokenService oauth2TokenService, EmailSenderResolver senderResolver) {
		this.oauth2TokenService = oauth2TokenService;
		this.senderResolver = senderResolver;
	}

	/** Which sender identity (security@/billing@/support@/updates@) this service's emails use. */
	protected abstract EmailCategory getCategory();

	/** The help address injected into templates as {{support_email}}. */
	protected String supportEmail() {
		return this.senderResolver.supportEmail();
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

			var sender = this.senderResolver.resolve(getCategory());
			// Graph rejects userId != from unless the mailbox has SendAs on the From address.
			log.debug("Sending {} email via mailbox '{}' with From '{}'", getCategory(), sender.userId(), sender.from());

			// Specify the "From" address (shared mailbox)
			EmailAddress fromAddress = new EmailAddress();
			fromAddress.setAddress(sender.from());
			message.setFrom(new Recipient());
			Objects.requireNonNull(message.getFrom()).setEmailAddress(fromAddress);

			// Replies always funnel to support, whatever the From category is.
			var replyTo = this.senderResolver.replyTo();
			if (replyTo != null) {
				var replyToAddress = new EmailAddress();
				replyToAddress.setAddress(replyTo);
				var replyToRecipient = new Recipient();
				replyToRecipient.setEmailAddress(replyToAddress);
				message.setReplyTo(List.of(replyToRecipient));
			}

			var accessToken = this.oauth2TokenService.getAccessToken();

			AuthenticationProvider authProvider = (request, _) -> request.headers.put("Authorization", Set.of("Bearer " + accessToken));
			var graphClient = new GraphServiceClient(authProvider);

			var postRequest = new SendMailPostRequestBody();
			postRequest.setMessage(message);

			graphClient
				.users()
				.byUserId(sender.userId())
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

	public abstract void send(UserDTO user, String languageCode) throws QorvaException;
}
