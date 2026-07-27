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

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private String cvId;

	private String candidateEmail;

	private String tokenHash;

	private String status;

	private String language;

	private Instant sentAt;
	private Instant openedAt;
	private Instant completedAt;
	private Instant expiresAt;
}
