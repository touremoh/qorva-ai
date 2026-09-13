package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Resume chat prompt budget. Every turn sends the CV/job/report context, the rolling
 * summary and a token-bounded window of recent turns — never the whole transcript — so
 * prompt size plateaus instead of growing with the conversation.
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

	/** Newest messages that are always sent verbatim and never folded into the summary. */
	private int keepRecentMessages = 6;

	/** Un-summarised history above this many estimated tokens triggers a compaction. */
	private int summaryTriggerTokens = 6000;

	/** Cheap model that maintains the rolling summary. */
	private String summaryModel = "gpt-4.1-mini";

	/** Cap on the summary's length, in model output tokens. */
	private int summaryMaxTokens = 1200;

	/** Log a warning when the CV + job + report block alone exceeds this many estimated tokens. */
	private int contextTokenWarn = 12000;
}
