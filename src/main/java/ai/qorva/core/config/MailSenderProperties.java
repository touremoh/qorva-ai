package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-category sender mailboxes. Keys of {@code senders} are lower-case
 * {@link ai.qorva.core.enums.EmailCategory} names (security/billing/support/updates).
 * A category with a blank {@code from} falls back to the legacy spring.mail.* sender,
 * so deploying before the mailboxes exist is a no-op.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qorva.mail")
public class MailSenderProperties {

	/** Universal Reply-To (support address). Blank → no Reply-To header is set. */
	private String replyTo;

	private Map<String, Sender> senders = new HashMap<>();

	@Getter
	@Setter
	public static class Sender {
		/** Graph mailbox the API sends as (users/{id}/sendMail). */
		private String userId;
		/** The From header address. */
		private String from;
	}
}
