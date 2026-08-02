package ai.qorva.core.enums;

/**
 * Intent-based sender identity for outgoing email. Each category maps to a dedicated
 * mailbox (security@/billing@/support@/updates@) via {@code qorva.mail.senders};
 * unset categories fall back to the legacy single sender (spring.mail.*).
 */
public enum EmailCategory {
	/** Credentials and credential links: temp passwords, set-password, password resets. */
	SECURITY,
	/** Anything triggered by Stripe/billing events. */
	BILLING,
	/** Support-initiated mail; also the universal Reply-To for every category. */
	SUPPORT,
	/** Account information, notifications, candidate-facing update requests. */
	UPDATES
}
