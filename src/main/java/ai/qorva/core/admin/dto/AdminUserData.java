package ai.qorva.core.admin.dto;

import java.time.Instant;
import java.util.List;

/** Users of a company as the admin console sees them. */
public final class AdminUserData {

	private AdminUserData() {
	}

	public record TenantUser(String id, String tenantId, String firstName, String lastName, String email,
	                         String userAccountStatus, boolean owner, List<String> actions, boolean mfaEnabled,
	                         boolean invitePending, Instant invitedAt, Instant lastLoginAt, Instant createdAt) {}

	public record InviteRequest(String firstName, String lastName, String email, List<String> authorities, String language) {}

	public record UpdateRequest(String firstName, String lastName) {}

	public record AuthoritiesRequest(List<String> actions) {}

	public record StatusRequest(String status) {}
}
