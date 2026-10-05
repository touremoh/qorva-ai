package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Prompt budget of Copilot's candidate answers ({@code ask_about_candidate}). Every answer sends the
 * CV/job/report context and a token-bounded window of the conversation's recent turns — never the
 * whole transcript — so prompt size plateaus instead of growing with the conversation.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qorva.ai.resume-chat")
public class ResumeChatProperties {

	/** Model answering the recruiter (gpt-5-chat-latest was retired 2026-07-23). */
	private String model = "gpt-5.6-sol";

	/** Max estimated tokens of verbatim recent messages sent to the model. */
	private int historyTokenBudget = 8000;

	/** Newest messages that are always sent verbatim, whatever the budget. */
	private int keepRecentMessages = 6;

	/** Log a warning when the CV + job + report block alone exceeds this many estimated tokens. */
	private int contextTokenWarn = 12000;
}
