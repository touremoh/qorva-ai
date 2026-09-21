package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/**
 * A recruiter's own mailbox, connected through delegated OAuth so Qorva can send candidate
 * outreach *as* them (the message lands in their Sent folder). One per user. Tokens are stored
 * AES-GCM encrypted and never leave the service layer.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "mailbox_connections")
public class MailboxConnection implements QorvaEntity {

	public static final String STATUS_ACTIVE = "ACTIVE";
	public static final String STATUS_REAUTH_REQUIRED = "REAUTH_REQUIRED";

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	/** {@link User#getId()} of the owner — the only user who may send through this connection. */
	private String userId;

	/** {@link ai.qorva.core.enums.MailboxProviderEnum} name. */
	private String provider;

	/** The mailbox actually authorised at consent (Graph /me), shown in the UI and used as From. */
	private String emailAddress;

	/** Encrypted {@link ai.qorva.core.service.mailbox.MailboxTokens}. */
	private String encryptedTokens;

	/** {@link #STATUS_ACTIVE} | {@link #STATUS_REAUTH_REQUIRED}. */
	private String status;

	private String lastError;
	private Instant connectedAt;
	private Instant lastUsedAt;

	@LastModifiedDate
	private Instant lastUpdatedAt;
}
