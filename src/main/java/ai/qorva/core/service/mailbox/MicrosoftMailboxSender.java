package ai.qorva.core.service.mailbox;

import ai.qorva.core.dao.entity.MailboxConnection;
import ai.qorva.core.enums.MailboxProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.odataerrors.ODataError;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import com.microsoft.kiota.authentication.AuthenticationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * Graph with a <em>delegated</em> token: everything goes through {@code /me}, so the message is
 * sent by, and saved to the Sent Items of, the recruiter who connected the mailbox.
 *
 * <p>Uses {@code /me/sendMail}, the only mail call {@code Mail.Send} authorises. It answers 202 and
 * nothing else — no message id, no webLink — so the outreach row cannot deep-link to the message.
 * The alternative (create a draft, then send it) would need {@code Mail.ReadWrite}, a grant that lets
 * the app read the whole mailbox and contradicts what the Profile card promises. The recruiter's
 * Sent folder is the source of truth; the history row links to it generically.</p>
 */
@Slf4j
@Component
public class MicrosoftMailboxSender implements MailboxSender {

	/** Outlook web's Sent Items folder — the closest thing to a link when sendMail returns no id. */
	static final String SENT_ITEMS_WEB_LINK = "https://outlook.office.com/mail/sentitems";

	@Override
	public MailboxProviderEnum provider() {
		return MailboxProviderEnum.MICROSOFT;
	}

	@Override
	public SendResult send(MailboxConnection connection, MailboxTokens tokens, String to, String subject,
	                       String textBody) throws QorvaException {
		try {
			var request = new SendMailPostRequestBody();
			request.setMessage(message(to, subject, textBody));
			request.setSaveToSentItems(true);
			client(tokens.getAccessToken()).me().sendMail().post(request);
			return new SendResult(null, null, SENT_ITEMS_WEB_LINK);
		} catch (ODataError e) {
			throw mapGraphError(e, connection);
		} catch (Exception e) {
			log.warn("Graph send failed for mailbox {}: {}", connection.getEmailAddress(), e.getMessage());
			throw new QorvaException(QorvaErrorCodes.MAILBOX_SEND_FAILED,
				HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
		}
	}

	@Override
	public String resolveEmailAddress(MailboxTokens tokens) throws QorvaException {
		try {
			var me = client(tokens.getAccessToken()).me().get(config ->
				config.queryParameters.select = new String[] {"mail", "userPrincipalName"});
			if (me == null) return null;
			return StringUtils.hasText(me.getMail()) ? me.getMail() : me.getUserPrincipalName();
		} catch (ODataError e) {
			throw mapGraphError(e, null);
		} catch (Exception e) {
			throw new QorvaException(QorvaErrorCodes.MAILBOX_SEND_FAILED,
				HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
		}
	}

	private static Message message(String to, String subject, String textBody) {
		var address = new EmailAddress();
		address.setAddress(to);
		var recipient = new Recipient();
		recipient.setEmailAddress(address);

		var body = new ItemBody();
		body.setContentType(BodyType.Text);
		body.setContent(textBody);

		var message = new Message();
		message.setToRecipients(List.of(recipient));
		message.setSubject(subject);
		message.setBody(body);
		return message;
	}

	private static GraphServiceClient client(String accessToken) {
		AuthenticationProvider authProvider = (request, _) ->
			request.headers.put("Authorization", Set.of("Bearer " + accessToken));
		return new GraphServiceClient(authProvider);
	}

	private static QorvaException mapGraphError(ODataError e, MailboxConnection connection) {
		var status = e.getResponseStatusCode();
		var code = e.getError() != null ? e.getError().getCode() : null;
		log.warn("Graph error {} ({}) for mailbox {}", status, code,
			connection != null ? connection.getEmailAddress() : "?");
		if (status == 401 || "InvalidAuthenticationToken".equals(code)) {
			return new QorvaException(QorvaErrorCodes.MAILBOX_REAUTH_REQUIRED,
				HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}
		return new QorvaException(QorvaErrorCodes.MAILBOX_SEND_FAILED,
			HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
	}
}
