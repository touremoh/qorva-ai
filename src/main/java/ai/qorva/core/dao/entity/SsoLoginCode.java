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
 * The hand-off between the Microsoft sign-in callback and the app: the browser carries a random code in the URL,
 * never the session token. Only its SHA-256 is stored; it is exchanged once (find-and-delete) within a minute,
 * and a TTL index removes the ones never used.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sso_login_codes")
public class SsoLoginCode {

	@Id
	private String id;

	private String codeHash;

	@Field(targetType = FieldType.OBJECT_ID)
	private String userId;

	@Field(targetType = FieldType.OBJECT_ID)
	private String tenantId;

	private Instant expiresAt;

	private Instant createdAt;
}
