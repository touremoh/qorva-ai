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
 * A tokenized invitation for a candidate to refresh their own availability/salary/CV.
 * Only the SHA-256 of the token is stored — the plaintext token exists solely in the
 * emailed link. Single-use for submission, time-boxed by {@code expiresAt}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "candidate_update_requests")
public class CandidateUpdateRequest {

	public static final String STATUS_SENT = "SENT";
	public static final String STATUS_OPENED = "OPENED";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_EXPIRED = "EXPIRED";

	// Async submission lifecycle (file uploads only; fields-only updates complete synchronously).
	public static final String STATUS_SUBMITTED = "SUBMITTED";
	public static final String STATUS_PROCESSING = "PROCESSING";
	public static final String STATUS_SUBMIT_FAILED = "SUBMIT_FAILED";   // retryable — token not consumed

	public static final String STAGE_PARSING = "PARSING";
	public static final String STAGE_UPDATING = "UPDATING";

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String cvId;

	private String candidateEmail;

	private String tokenHash;

	private String status;

	private String language;

	/** Candidate-submitted fields (Submission JSON), persisted at enqueue time. */
	private String submissionPayload;

	/** S3 key of the staged resume file; deleted once processing reaches a terminal state. */
	private String pendingFileKey;
	private String originalFileName;

	/** PARSING | UPDATING while status is PROCESSING. */
	private String processingStage;
	private String processingError;

	/** Worker lease (mirrors BackgroundJob): stale PROCESSING claims are reclaimed. */
	private String leaseOwner;
	private Instant leaseExpiresAt;

	private Instant sentAt;
	private Instant openedAt;
	private Instant submittedAt;
	private Instant completedAt;
	private Instant expiresAt;
}
