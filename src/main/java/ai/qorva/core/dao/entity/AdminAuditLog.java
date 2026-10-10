package ai.qorva.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One change made from the admin console: who, what, on which tenant/user, from where. Kept by tenant purges. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "admin_audit_logs")
public class AdminAuditLog {

	@Id
	private String id;

	private Instant at;
	private String adminId;
	private String adminEmail;

	/** e.g. TENANT_SUSPENDED, TESTER_CREATED, USER_STATUS_CHANGED. */
	private String action;

	private String targetTenantId;
	private String targetUserId;

	/** Human-readable before/after, never a secret. */
	private String summary;
	private String ip;
}
