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

	/** Remaining counts are this period's, null when the plan has no limit for them. */
	public record Availability(boolean enabled, boolean rulesEnabled, Integer runsRemaining, Integer candidateQuestionsRemaining,
	                           Integer libraryAnalysesRemaining, boolean canViewTeam) {}

	@Getter
	@Setter
	@NoArgsConstructor
	public static class StartRunRequest {
		private String goal;
		private List<MentionView> mentions = new ArrayList<>();
		/** Continue this conversation; absent or unknown starts a new one. */
		private String conversationId;
		/** IANA zone of the browser (e.g. Europe/Paris), used when the chat proposes a scheduled rule. */
		private String timeZone;
		/** The candidate and job the conversation is about; absent keeps the conversation's focus, if any. */
		private FocusRequest focus;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	public static class FocusRequest {
		private String cvId;
		private String jobPostId;
	}

	public record FocusView(String cvId, String cvName, String jobPostId, String jobTitle) {}

	/** CV or JOB. */
	public record MentionView(String type, String id, String name) {}

	public record LinkView(String type, String id, String label) {}

	public record DraftView(String cvId, String jobId, String subject, String body) {}

	public record StepView(int seq, String kind, String tool, String state, String summaryKey,
	                       Map<String, String> summaryParams, List<LinkView> links, DraftView draft, boolean autoApproved) {}

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
		String ruleId,
		String ruleName,
		String userEmail,
		String status,
		String goal,
		List<MentionView> mentions,
		FocusView focus,
		List<StepView> steps,
		List<ActionView> pendingActions,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant approvalExpiresAt,
		String finalAnswer,
		/** Charts, metrics and candidate cards of a library analysis; null for other answers. */
		AnswerBlocks blocks,
		String failureReason,
		boolean stoppedEarly,
		boolean canCancel,
		boolean canApprove,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant createdAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant finishedAt,
		/** A candidate answer its user may keep as a note ("Save as note"), not saved yet. */
		boolean canSaveAnswerAsNote,
		/** The note it was saved as. */
		String answerNoteId) {}

	/** Activity row: a run without its steps and answer. */
	public record RunSummary(
		String id,
		String conversationId,
		String title,
		String origin,
		String ruleId,
		String ruleName,
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

	/** What a rule watches. Fields not used by {@code type} are ignored. */
	@Getter
	@Setter
	@NoArgsConstructor
	public static class TriggerRequest {
		private String type;
		private String source;
		private String jobPostId;
		private Integer minScore;
		private Boolean recommendedOnly;
		private String frequency;
		private Integer hour;
		private Integer weekday;
		private String zoneId;
		private String connectionId;
		/** JOB_NEEDS_MATCHING: stale reasons that fire it; null or empty = all. */
		private List<String> staleReasons;
		/** REPORT_STATUS_CHANGED: statuses that fire it; REPORT_STATUS_IDLE: statuses watched; null or empty = any. */
		private List<String> toStatuses;
		/** CV_SCORED: highest final score that fires it. */
		private Integer maxScore;
		/** CV_SCORED: report verdicts that fire it; null or empty = any. */
		private List<String> recommendations;
		/** REPORT_STATUS_IDLE: days without a status change (1–90). */
		private Integer idleDays;
		/** CV_OUTDATED: content age in months (6, 12, 18 or 24). */
		private Integer staleMonths;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	public static class RuleRequest {
		private String name;
		private TriggerRequest trigger;
		private String goalTemplate;
		/** Null: the default cap. */
		private Integer dailyRunCap;
		/** Run matching without asking, up to {@code autoApproveMaxActions} actions per matching. */
		private Boolean autoApproveMatching;
		private Integer autoApproveMaxActions;
		/** Send profile-update requests without asking, up to {@code autoApproveProfileUpdatesMax} candidates per request. */
		private Boolean autoApproveProfileUpdates;
		private Integer autoApproveProfileUpdatesMax;
	}

	public record TriggerView(String type, String source, String jobPostId, String jobTitle, Integer minScore,
	                          Boolean recommendedOnly, String frequency, Integer hour, Integer weekday, String zoneId,
	                          String connectionId, String connectionName, List<String> staleReasons,
	                          List<String> toStatuses, Integer maxScore, List<String> recommendations, Integer idleDays,
	                          Integer staleMonths) {}

	public record RuleView(
		String id,
		String name,
		String ownerEmail,
		TriggerView trigger,
		String goalTemplate,
		int dailyRunCap,
		boolean autoApproveMatching,
		Integer autoApproveMaxActions,
		boolean autoApproveProfileUpdates,
		Integer autoApproveProfileUpdatesMax,
		String status,
		String pausedReason,
		int runsToday,
		int skippedToday,
		String lastRunId,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant lastFiredAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant nextRunAt,
		@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant createdAt,
		/** Owner only: edit and delete. */
		boolean canEdit,
		/** Owner, or a user who manages users: pause and resume. */
		boolean canPause) {}
}
