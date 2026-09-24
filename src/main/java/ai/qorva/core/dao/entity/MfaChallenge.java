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
 * One pending email MFA code. The id is an opaque random value handed to the client in a request
 * body — deliberately not a JWT, so it can never pass for a session in {@code JwtRequestFilter}.
 * Only the BCrypt hash of the code is stored; expired rows are reaped by a TTL index.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "mfa_challenges")
public class MfaChallenge implements QorvaEntity {

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	/** {@link User#getId()} the code was sent to. */
	private String userId;

	/** {@link ai.qorva.core.enums.MfaPurpose} name. */
	private String purpose;

	private String codeHash;
	private int attempts;
	private int sends;
	private Instant lastSentAt;
	private Instant expiresAt;

	/** Set once the challenge succeeded or was burnt by too many wrong codes; never reusable after. */
	private Instant consumedAt;

	private Instant createdAt;
}
