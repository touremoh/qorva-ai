package ai.qorva.core.service.mailbox;

import ai.qorva.core.config.MailboxProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.qorva.core.security.JsonSecretCipher;
import org.springframework.stereotype.Component;

/** {@link MailboxTokens} ⇄ encrypted blob, keyed by {@code qorva.mailbox.credentials-key} (independent of the ATS key). */
@Component
public class MailboxTokenCipher extends JsonSecretCipher<MailboxTokens> {

	public MailboxTokenCipher(MailboxProperties properties, ObjectMapper objectMapper) {
		super(properties.getCredentialsKey(), "qorva.mailbox.credentials-key", MailboxTokens.class, objectMapper);
	}
}
