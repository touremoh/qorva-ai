package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/**
 * Queued score write-back to an ATS. Written when a matching report lands for an
 * ATS-linked CV and drained by AtsWriteBackService with retry/backoff — a provider
 * outage must never block report generation (same pattern as PendingEmailNotification).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ats_outbound_tasks")
public class AtsOutboundTask implements QorvaEntity {

	public static final String STATUS_PENDING = "PENDING";
	/** Claimed by a drain and in flight; reclaimable once the claim lease expires. */
	public static final String STATUS_SENDING = "SENDING";
	public static final String STATUS_SENT = "SENT";
	public static final String STATUS_FAILED = "FAILED";

	public static final int MAX_ATTEMPTS = 5;

	/** How long a claim holds a task before another drain may reclaim it. */
	public static final long CLAIM_LEASE_MINUTES = 10;

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String connectionId;
	private String provider;

	private String cvId;
	private String externalCandidateId;
	private String externalApplicationId;

	private String jobTitle;
	private Double score;
	private String headline;
	private String reportUrl;

	private String status;
	private int attempts;
	/** Due time for the next drain: the backoff deadline when PENDING, the claim lease when SENDING. */
	private Instant nextAttemptAt;
	private String lastError;

	private Instant createdAt;
	private Instant sentAt;
}
