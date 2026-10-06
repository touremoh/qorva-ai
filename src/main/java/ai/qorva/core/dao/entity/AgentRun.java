package ai.qorva.core.dao.entity;

import ai.qorva.core.dto.AnswerBlocks;
import ai.qorva.core.dto.ConversationFrame;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One Copilot task: the user's goal, what the agent did step by step, and the model conversation
 * needed to resume it on any instance. Written by the API (create, cancel request) and by the
 * worker that holds its lease (everything else).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "agent_runs")
public class AgentRun implements QorvaEntity {

	public static final String STATUS_QUEUED = "QUEUED";
	public static final String STATUS_RUNNING = "RUNNING";
	public static final String STATUS_AWAITING_APPROVAL = "AWAITING_APPROVAL";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_FAILED = "FAILED";
	public static final String STATUS_CANCELLED = "CANCELLED";
	public static final String STATUS_EXPIRED = "EXPIRED";

	/** A user has at most one run in these states at a time. */
	public static final List<String> ACTIVE_STATUSES = List.of(STATUS_QUEUED, STATUS_RUNNING, STATUS_AWAITING_APPROVAL);

	public static final String ORIGIN_CHAT = "CHAT";
	/** Started by a standing rule (AgentRule), as the rule's owner. */
	public static final String ORIGIN_RULE = "RULE";

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String conversationId;
	/** First goal of the conversation, shortened; set on every run of it for cheap listing. */
	private String title;
	private String userEmail;
	private String language;
	private String origin;
	/** RULE runs: the rule that started it, and its name at the time. */
	private String ruleId;
	private String ruleName;
	/** RULE runs: the rule's matching pre-approval at the time it fired — max actions per matching, null = none. */
	private Integer autoApproveMaxActions;
	/** The recruiter's time zone (from the browser), used when a chat proposes a scheduled rule. */
	private String timeZone;

	private String goal;
	private List<Mention> mentions = new ArrayList<>();
	/** The candidate and job the conversation is about (opened from a report or a CV); carried to its next runs. */
	private Focus focus;

	private String status;
	private List<Step> steps = new ArrayList<>();
	/** Model conversation, needed to continue the run; never exposed through the API. */
	private List<HistoryMessage> history = new ArrayList<>();
	private String finalAnswer;
	/** Charts, metrics and candidate cards of a library analysis answer; null for other answers. */
	private AnswerBlocks blocks;
	/** Talent Intelligence state after this run's library analysis, read by the next one so follow-ups keep their filters. */
	private ConversationFrame insightFrame;
	/** Error key when the run FAILED, e.g. error.agent.* — shown translated by the app. */
	private String failureReason;
	/** True when a budget ended the run before the model finished. */
	private Boolean stoppedEarly;

	/** Approval actions of the turn that paused the run; decided by the user, executed by the worker on resume. */
	private List<PendingAction> pendingActions = new ArrayList<>();
	/** Results of the same turn's non-approval calls, held until the pending actions are decided (one tool message per turn). */
	private List<ToolResult> pendingToolResults = new ArrayList<>();
	private Instant approvalExpiresAt;
	/** Messages sent to candidates in this run (capped by qorva.ai.agent.max-outbound-per-run). */
	private int outboundCount;
	/** RULE runs: when the owner was emailed about this pause; cleared on every new pause. */
	private Instant notifiedAt;

	private Tokens tokens = new Tokens();
	private int stepCount;
	private int toolCallCount;
	private long runningMillis;
	/**
	 * The run counts once against the agentRuns meter when it uses a tool other than a terminal answer tool, or
	 * answers in its own words. A run answered by a terminal tool counts against that tool's meter instead.
	 */
	private boolean metered;

	private String leaseOwner;
	private Instant leaseExpiresAt;
	private boolean cancelRequested;

	@CreatedDate
	private Instant createdAt;
	private Instant startedAt;
	private Instant finishedAt;

	@LastModifiedDate
	private Instant lastUpdatedAt;

	@CreatedBy
	private String createdBy;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Mention {
		/** CV or JOB. */
		private String type;
		private String id;
		private String name;
	}

	/** A candidate for a job: what "this candidate" means in the conversation. */
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Focus {
		private String cvId;
		private String cvName;
		private String jobPostId;
		private String jobTitle;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Step {
		public static final String KIND_TOOL_CALL = "TOOL_CALL";
		public static final String STATE_EXECUTING = "EXECUTING";
		public static final String STATE_OK = "OK";
		public static final String STATE_ERROR = "ERROR";
		/** Proposed approval action, waiting for the user. */
		public static final String STATE_PENDING = "PENDING";
		public static final String STATE_REJECTED = "REJECTED";
		public static final String STATE_EXPIRED = "EXPIRED";

		private int seq;
		private String kind;
		private String tool;
		private String tier;
		/** An approval-tier action carried out without asking, under its rule's pre-approval. */
		private Boolean autoApproved;
		private String state;
		/** i18n key of the user-facing line for this step (e.g. agent.step.search_cvs), translated by the app. */
		private String summaryKey;
		/** Values interpolated into the summary, e.g. {count: "12"}. */
		private java.util.Map<String, String> summaryParams = new java.util.LinkedHashMap<>();
		private List<Link> links = new ArrayList<>();
		/** Email draft produced by the step, so the app can open it in the composer. */
		private Draft draft;
		private String error;
		private Instant startedAt;
		private Instant finishedAt;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Draft {
		private String cvId;
		private String jobId;
		private String subject;
		private String body;
	}

	/** An approval-tier call waiting for (or decided by) the run's user. */
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PendingAction {
		public static final String PENDING = "PENDING";
		public static final String APPROVED = "APPROVED";
		public static final String REJECTED = "REJECTED";
		/** Execution started; if the worker died here, the outcome is unknown and it is never retried. */
		public static final String EXECUTING = "EXECUTING";
		public static final String DONE = "DONE";

		private String actionId;
		private int stepSeq;
		private String toolCallId;
		private String tool;
		private String argsJson;
		/** Binds a decision to exactly the arguments the user saw. */
		private String argsHash;
		/** What the card shows, built and checked by the tool when the action was proposed. */
		private java.util.Map<String, Object> preview = new java.util.LinkedHashMap<>();
		private String status;
		private String editedSubject;
		private String editedBody;
		private String reason;
		private Instant decidedAt;
		/** The result sent to the model, once executed or declined. */
		private String resultJson;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Link {
		/** CV, JOB or REPORT. */
		private String type;
		private String id;
		private String label;
	}

	/** One model message, in a shape Mongo can store and the runner can turn back into Spring AI messages. */
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class HistoryMessage {
		/** system, user, assistant or tool. */
		private String role;
		private String text;
		private List<ToolCall> toolCalls;
		private List<ToolResult> toolResults;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ToolCall {
		private String id;
		private String name;
		private String arguments;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ToolResult {
		private String id;
		private String name;
		private String data;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Tokens {
		private long prompt;
		private long completion;
		private String model;

		public long total() {
			return prompt + completion;
		}
	}
}
