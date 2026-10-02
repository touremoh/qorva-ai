package ai.qorva.core.dao.entity;

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
import java.util.List;

/**
 * A standing rule: when its trigger sees new records, a Copilot run is started for them, as the rule's
 * owner. Edited by its owner through the API; the scheduler that holds its lease moves the watermark and
 * counters (field-level updates in {@code AgentRuleStore}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "agent_rules")
public class AgentRule implements QorvaEntity {

	public static final String TRIGGER_CV_ADDED = "CV_ADDED";
	public static final String TRIGGER_CV_SCORED = "CV_SCORED";
	public static final String TRIGGER_SCHEDULE = "SCHEDULE";
	public static final String TRIGGER_ATS_SYNC_FINISHED = "ATS_SYNC_FINISHED";
	public static final List<String> TRIGGERS = List.of(TRIGGER_CV_ADDED, TRIGGER_CV_SCORED, TRIGGER_SCHEDULE, TRIGGER_ATS_SYNC_FINISHED);

	public static final String STATUS_ACTIVE = "ACTIVE";
	public static final String STATUS_PAUSED = "PAUSED";

	/** Paused by its owner or an admin; only a person resumes it. */
	public static final String PAUSED_MANUAL = "MANUAL";
	/** The owner was deleted, locked, or lost USE_AGENT. */
	public static final String PAUSED_OWNER_UNAVAILABLE = "OWNER_UNAVAILABLE";
	/** The plan's Copilot runs are used up; resumes by itself when capacity is back. */
	public static final String PAUSED_QUOTA = "QUOTA";
	/** The subscription is blocked; resumes by itself when it is active again. */
	public static final String PAUSED_SUBSCRIPTION = "SUBSCRIPTION_INACTIVE";
	public static final String PAUSED_JOB_DELETED = "JOB_DELETED";
	public static final String PAUSED_CONNECTION_REMOVED = "CONNECTION_REMOVED";
	/** Pauses the scheduler lifts on its own once the cause is gone. */
	public static final List<String> SELF_RESUMING = List.of(PAUSED_QUOTA, PAUSED_SUBSCRIPTION);

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	/** The rule runs as this user: their rights, their mailbox, their language. */
	private String ownerEmail;
	private String language;

	private String name;
	private Trigger trigger;
	private String goalTemplate;
	private int dailyRunCap;

	private String status;
	private String pausedReason;
	private Instant pausedAt;

	/** Records newer than this have not been looked at yet. Set to "now" on creation, resume and trigger change. */
	private Instant watermark;
	/** Next slot of a SCHEDULE rule. */
	private Instant nextRunAt;

	/** Day (UTC, yyyy-MM-dd) the counters below belong to. */
	private String countersDay;
	private int runsToday;
	/** Records seen after the daily cap was reached; they are not fired later. */
	private int skippedToday;
	private Instant lastFiredAt;
	private String lastRunId;

	/** Scheduler lease, and when the rule is next due a check. */
	private String leaseOwner;
	private Instant leaseExpiresAt;
	private Instant nextCheckAt;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant lastUpdatedAt;

	@CreatedBy
	private String createdBy;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Trigger {
		public static final String SOURCE_ANY = "ANY";
		public static final String SOURCE_ATS = "ATS";
		public static final String SOURCE_MANUAL = "MANUAL";
		public static final String DAILY = "DAILY";
		public static final String WEEKLY = "WEEKLY";

		private String type;
		/** CV_ADDED: ANY, ATS (imported) or MANUAL (uploaded). */
		private String source;
		/** CV_SCORED: one job, or any job when null. */
		private String jobPostId;
		private String jobTitle;
		/** CV_SCORED: final score at or above this (0–100). */
		private Integer minScore;
		/** CV_SCORED: only reports recommending an interview (interview or strong_interview). */
		private Boolean recommendedOnly;
		/** SCHEDULE: DAILY or WEEKLY, at {@code hour} in {@code zoneId}; WEEKLY also on {@code weekday} (1 = Monday). */
		private String frequency;
		private Integer hour;
		private Integer weekday;
		private String zoneId;
		/** ATS_SYNC_FINISHED: one connection, or any when null. */
		private String connectionId;
		private String connectionName;
	}
}
