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
 * A Qorva staff member who signs in to the admin console. Deliberately not a {@code User}: it belongs to no
 * tenant, never passes the tenant JWT filter, and a tenant permission can never turn into one.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platform_admins")
public class PlatformAdmin {

	public static final String ROLE_OWNER = "OWNER";
	public static final String ROLE_SUPPORT = "SUPPORT";
	public static final String STATUS_ACTIVE = "ACTIVE";
	public static final String STATUS_DISABLED = "DISABLED";

	@Id
	private String id;

	/** Stored lower-cased; unique. */
	private String email;
	private String firstName;
	private String lastName;

	/** BCrypt; null until the admin has used their set-password link. */
	private String encryptedPassword;

	private String role;
	private String status;

	/** Embedded in admin tokens and set-password links; bumped on every password change, role change or disable. */
	private int credentialVersion;

	private Instant lastLoginAt;
	private Instant invitedAt;
	private String invitedBy;
	private Instant createdAt;
	private Instant updatedAt;
}
