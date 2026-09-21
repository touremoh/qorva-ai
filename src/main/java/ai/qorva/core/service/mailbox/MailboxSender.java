package ai.qorva.core.service.mailbox;

import ai.qorva.core.dao.entity.MailboxConnection;
import ai.qorva.core.enums.MailboxProviderEnum;
import ai.qorva.core.exception.QorvaException;

/**
 * Sends one plain-text email as the connected user. One implementation per
 * {@link MailboxProviderEnum}; {@link MailboxConnectionService} picks it by the connection's provider.
 */
public interface MailboxSender {

	/**
	 * What the provider told us about the sent message. Ids are null when the provider's send call
	 * returns none (Microsoft {@code sendMail}); {@code webLink} then points at the Sent folder.
	 */
	record SendResult(String providerMessageId, String providerThreadId, String webLink) {}

	MailboxProviderEnum provider();

	/**
	 * @param tokens fresh tokens (already refreshed by the caller)
	 * @throws QorvaException {@code error.mailbox.reauth_required} when the provider rejects the token,
	 *                        {@code error.mailbox.send_failed} for anything else
	 */
	SendResult send(MailboxConnection connection, MailboxTokens tokens, String to, String subject, String textBody)
		throws QorvaException;

	/** The address of the mailbox behind these tokens, read from the provider at consent time. */
	String resolveEmailAddress(MailboxTokens tokens) throws QorvaException;
}
