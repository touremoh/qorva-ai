package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** API shapes for Copilot ({@code /agent}). The model history, raw tool arguments and tokens are never exposed. */
public final class AgentData {

	private AgentData() {}

	public record Availability(boolean enabled, boolean rulesEnabled, Integer runsRemaining, boolean canViewTeam) {}

	@Getter
	@Setter
	@NoArgsConstructor
	public static class StartRunRequest {
		private String goal;
		private List<MentionView> mentions = new ArrayList<>();
		/** Continue this conversation; absent or unknown starts a new one. */
		private String conversationId;
	}

	/** CV or JOB. */
	public record MentionView(String type, String id, String name) {}

	public record LinkView(String type, String id, String label) {}

	public record DraftView(String cvId, String jobId, String subject, String body) {}

	public record StepView(int seq, String kind, String tool, String state, String summaryKey,
	                       Map<String, String> summaryParams, List<LinkView> links, DraftView draft) {}

	/** An approval card: what the tool proposes, as checked when it was proposed. */
	public record ActionView(String actionId, int stepSeq, String tool, String status, String argsHash,
	                         Map<String, Object> preview, String reason) {}

	@Getter
	@Setter
	@NoArgsConstructor
	public static class DecisionRequest {
		/** The argsHash of the card the user decided on: a changed or re-proposed action is refused. */
		private String argsHash;
		/** Email cards only: the user's edits (null = unchanged). */
		private String subject;
		private String body;
		/** Reject only. */
		private String reason;
	}

	public record RunView(
		String id,
		String conversationId,
		String title,
		String origin,
		String userEmail,
		String status,
		String goal,
		List<MentionView> mentions,
		List<StepView> steps,
		List<ActionView> pendingActions,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant approvalExpiresAt,
		String finalAnswer,
		String failureReason,
		boolean stoppedEarly,
		boolean canCancel,
		boolean canApprove,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant createdAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant finishedAt) {}

	/** Activity row: a run without its steps and answer. */
	public record RunSummary(
		String id,
		String conversationId,
		String title,
		String origin,
		String userEmail,
		String status,
		String goal,
		int stepCount,
		String failureReason,
		boolean canCancel,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant createdAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant finishedAt) {}

	public record RunPage(List<RunSummary> items, int page, int size, long total) {}

	public record ConversationSummary(
		String conversationId,
		String title,
		String lastStatus,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant lastActivityAt) {}

	public record Count(long count) {}
}
