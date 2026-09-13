package ai.qorva.core.service.orchestrators;

import ai.qorva.core.config.ResumeChatProperties;
import ai.qorva.core.dao.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** Folds older resume-chat turns into the rolling summary with a cheap model. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSummarizerAgent {

	private static final String INSTRUCTIONS = """
		You maintain a running summary of a recruiter's conversation with an assistant about one candidate for one job.
		Merge the PREVIOUS SUMMARY and the NEW MESSAGES into one updated summary.
		Keep: the questions asked and the conclusions reached, facts the recruiter stated about the candidate or the \
		hiring process, decisions taken, open points, and the recruiter's language and tone preferences.
		Drop pleasantries, repeated context and anything already implied by the CV or job description.
		At most 300 words, as bullet points, written in the same language as the conversation.
		Return only the summary.
		""";

	private final ChatClient chatClient;
	private final ResumeChatProperties properties;

	public String summarize(String previousSummary, List<ChatMessage> messages) {
		StringBuilder sb = new StringBuilder();
		sb.append("PREVIOUS SUMMARY:\n").append(StringUtils.hasText(previousSummary) ? previousSummary : "(none)");
		sb.append("\n\nNEW MESSAGES:\n");
		for (ChatMessage m : messages) {
			sb.append(m.getRole().name()).append(": ").append(m.getContent()).append("\n\n");
		}

		String summary = chatClient.prompt()
			.options(OpenAiChatOptions.builder()
				.model(properties.getSummaryModel())
				.temperature(0.2)
				.maxCompletionTokens(properties.getSummaryMaxTokens())
				.build())
			.messages(new SystemMessage(INSTRUCTIONS), new UserMessage(sb.toString()))
			.call()
			.content();

		if (!StringUtils.hasText(summary)) {
			throw new IllegalStateException("Summarizer returned an empty summary");
		}
		return summary.trim();
	}
}
