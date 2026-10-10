package ai.qorva.core.admin.dto;

import java.time.Instant;
import java.util.Map;

/** Companies as the admin console sees them. */
public final class AdminTenantData {

	private AdminTenantData() {
	}

	public static final String TYPE_CUSTOMER = "CUSTOMER";
	public static final String TYPE_DEMO = "DEMO";
	public static final String TYPE_TESTER = "TESTER";

	public record TenantRow(String id, String tenantName, String contactEmail, String status, String accountType,
	                        boolean internal, Instant accessExpiresAt, String subscriptionPlan, String subscriptionStatus,
	                        long userCount, Instant createdAt) {}

	public record Subscription(String subscriptionPlan, String billingCycle, String priceId, String subscriptionStatus,
	                           String subscriptionId, Instant currentPeriodStart, Instant currentPeriodEnd, Boolean cancelAtPeriodEnd) {}

	public record Counts(long users, long cvs, long jobPosts, long openJobPosts, long matchingReports) {}

	public record Usage(Instant periodStart, Instant periodEnd, Map<String, Integer> used, Map<String, Integer> limits) {}

	public record TenantDetail(String id, String tenantName, String contactEmail, String status, String accountType,
	                           boolean internal, Instant accessExpiresAt, String subscriptionPlan, String subscriptionStatus,
	                           long userCount, Instant createdAt,
	                           String organizationId, String recruitmentType, String organizationSize, String companyAddress,
	                           String phoneNumber, String websiteUrl, Boolean ssoRequired, String stripeCustomerId,
	                           Subscription subscriptionInfo, String statusReason, Instant statusChangedAt, String statusChangedBy,
	                           Instant deletedAt, Instant purgeAfter, Counts counts, Usage usage, Instant lastUpdatedAt) {}

	public record CreateDemoRequest(String companyName, String firstName, String lastName, String email,
	                                String recruitmentType, String organizationSize, String language) {}

	public record UpdateTenantRequest(String tenantName, String contactEmail, String phoneNumber, String companyAddress,
	                                  String websiteUrl, String organizationSize, String recruitmentType) {}

	public record StatusRequest(String status, String reason) {}

	public record ReasonRequest(String reason) {}

	public record PurgeRequest(String confirmName) {}
}
