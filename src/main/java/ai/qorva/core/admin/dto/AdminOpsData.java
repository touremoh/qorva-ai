package ai.qorva.core.admin.dto;

import ai.qorva.core.dao.entity.AdminAuditLog;

import java.time.Instant;

/** Stripe event logs and the admin audit log, as the console lists them. */
public final class AdminOpsData {

	private AdminOpsData() {
	}

	public record StripeEventRow(String id, String stripeEventId, String eventType, String eventStatus, String tenantId,
	                             String tenantName, String stripeCustomerId, String stripeSubscriptionId, Instant createdAt,
	                             boolean inLedger) {}

	public record AuditEntry(String id, Instant at, String adminId, String adminEmail, String action, String targetTenantId,
	                         String targetUserId, String summary, String ip) {

		public static AuditEntry from(AdminAuditLog a) {
			return new AuditEntry(a.getId(), a.getAt(), a.getAdminId(), a.getAdminEmail(), a.getAction(), a.getTargetTenantId(),
				a.getTargetUserId(), a.getSummary(), a.getIp());
		}
	}
}
