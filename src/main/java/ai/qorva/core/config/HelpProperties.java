package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qorva Help, the in-app assistant for questions about using Qorva. It answers from the curated help text
 * only — no tools, no tenant data — and is free for every signed-in user, so these limits are what bounds it.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qorva.help")
public class HelpProperties {

	/** Kill switch: off → availability says so, the app hides the button and both endpoints answer 503. */
	private boolean enabled = false;

	/** Answers from a fixed help text: the mini tier is plenty. */
	private String model = "gpt-4.1-mini";

	/** Longest question a user may type. */
	private int maxMessageChars = 1000;

	/** Earlier exchanges of the conversation replayed to the model. */
	private int historyTurns = 6;

	/** Exchanges kept on a conversation document (older ones are dropped). */
	private int storedTurns = 20;

	private int maxOutputTokens = 700;

	/** Conversations are deleted this long after their last message (TTL index). */
	private int conversationTtlDays = 90;

	private Limits limits = new Limits();

	@Getter
	@Setter
	public static class Limits {
		private int perUserPerHour = 30;
		private int perUserPerDay = 150;
		private int perTenantPerDay = 1000;
		/** Demo users share demo tenants with many others: one tighter daily cap. */
		private int demoPerUserPerDay = 30;
		private int ticketsPerUserPerDay = 5;
	}
}
