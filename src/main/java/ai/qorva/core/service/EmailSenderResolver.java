package ai.qorva.core.service;

import ai.qorva.core.config.MailSenderProperties;
import ai.qorva.core.enums.EmailCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Resolves the (mailbox, from) pair for an email category, falling back to the legacy
 * single sender (spring.mail.username / spring.mail.from) when a category is not
 * configured — rollout is safe before the per-category mailboxes exist.
 */
@Component
public class EmailSenderResolver {

	public record ResolvedSender(String userId, String from) {}

	private final MailSenderProperties properties;

	@Value("${spring.mail.username}")
	private String legacyUserId;

	@Value("${spring.mail.from}")
	private String legacyFrom;

	public EmailSenderResolver(MailSenderProperties properties) {
		this.properties = properties;
	}

	public ResolvedSender resolve(EmailCategory category) {
		var sender = properties.getSenders().get(category.name().toLowerCase(Locale.ROOT));
		if (sender == null || !StringUtils.hasText(sender.getFrom())) {
			return new ResolvedSender(legacyUserId, legacyFrom);
		}
		var userId = StringUtils.hasText(sender.getUserId()) ? sender.getUserId() : sender.getFrom();
		return new ResolvedSender(userId, sender.getFrom());
	}

	/** Universal Reply-To (support). Null when not configured. */
	public String replyTo() {
		return StringUtils.hasText(properties.getReplyTo()) ? properties.getReplyTo() : null;
	}

	/** The address users should write to for help — shown as {{support_email}} in templates. */
	public String supportEmail() {
		var support = properties.getSenders().get(EmailCategory.SUPPORT.name().toLowerCase(Locale.ROOT));
		if (support != null && StringUtils.hasText(support.getFrom())) {
			return support.getFrom();
		}
		return replyTo() != null ? replyTo() : legacyFrom;
	}
}
