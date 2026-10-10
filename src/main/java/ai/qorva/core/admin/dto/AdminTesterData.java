package ai.qorva.core.admin.dto;

import java.time.Instant;

/** Test accounts: created and run from the console, no Stripe, access until a set instant. */
public final class AdminTesterData {

	private AdminTesterData() {
	}

	public static final String STATE_ACTIVE = "ACTIVE";
	public static final String STATE_EXPIRING_SOON = "EXPIRING_SOON";
	public static final String STATE_EXPIRED = "EXPIRED";
	public static final String STATE_DEACTIVATED = "DEACTIVATED";
	public static final String STATE_DELETED = "DELETED";

	public record TierRef(String productId, String name, String priceId) {}

	public record Tester(String tenantId, String tenantName, String userId, String firstName, String lastName, String email,
	                     String language, TierRef tier, Instant accessExpiresAt, String status, String state, Instant createdAt) {}

	public record CreateRequest(String firstName, String lastName, String email, String password, String productId,
	                            Instant accessExpiresAt, String companyName, String language) {}

	public record ExpiryRequest(Instant accessExpiresAt) {}

	public record TierRequest(String productId) {}

	public record ReactivateRequest(String password, Instant accessExpiresAt) {}

	public record PasswordRequest(String password) {}

	public record TierLimits(Integer screeningActions, Integer aiResumeChats, Integer talentIntelligenceQueries, Integer agentRuns,
	                         Integer emailTemplates, Integer bulkUploadFiles, Integer atsConnections, Integer matchingTopNMax,
	                         Integer matchingTopNDefault) {}

	public record Tier(String productId, String name, String priceId, Integer seats, TierLimits limits) {}
}
