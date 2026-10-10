package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One pending admin sign-in code: same rules as {@link MfaChallenge} (opaque random id, BCrypt hash of the code,
 * atomic attempt counter, TTL reaping), kept apart so admin and tenant sign-ins never share a collection.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "admin_mfa_challenges")
public class AdminMfaChallenge {

	@Id
	private String id;

	private String adminId;
	private String codeHash;
	private int attempts;
	private int sends;
	private Instant lastSentAt;
	private Instant expiresAt;
	private Instant consumedAt;
	private Instant createdAt;
}
