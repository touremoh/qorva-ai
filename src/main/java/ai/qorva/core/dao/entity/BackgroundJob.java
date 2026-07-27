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
import java.util.List;

/**
 * Asynchronous bulk operation (e.g. re-analyze all CVs matching an issue). Claimed by
 * worker instances via an atomic lease (findAndModify) so multiple App Runner instances
 * never double-process; stale leases from crashed instances are reclaimed.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "background_jobs")
public class BackgroundJob {

	public static final String TYPE_REANALYZE = "REANALYZE";
	public static final String TYPE_CANDIDATE_UPDATE_CAMPAIGN = "CANDIDATE_UPDATE_CAMPAIGN";

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_RUNNING = "RUNNING";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_COMPLETED_WITH_ERRORS = "COMPLETED_WITH_ERRORS";
	public static final String STATUS_FAILED = "FAILED";
	public static final String STATUS_CANCELLED = "CANCELLED";

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String type;

	/** Criteria: an issue key (criteria mode) — resolved to an indexed filter at execution time. */
	private String issueKey;

	/** Candidate-facing language for campaign emails (base code, e.g. "en"). */
	private String language;

	private String status;

	private long total;
	private long processed;
	private long succeeded;
	private long failed;
	private long skipped;

	/** Capped sample of per-item failures for diagnostics. */
	private List<String> errorSamples;

	private String failureReason;

	private String leaseOwner;
	private Instant leaseExpiresAt;

	private String createdBy;
	private Instant createdAt;
	private Instant startedAt;
	private Instant finishedAt;
}
