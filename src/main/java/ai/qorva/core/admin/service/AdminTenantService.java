package ai.qorva.core.admin.service;

import ai.qorva.core.admin.config.AdminProperties;
import ai.qorva.core.admin.dto.AdminJobData;
import ai.qorva.core.admin.dto.AdminTenantData;
import ai.qorva.core.admin.security.AdminContext;
import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dto.AccountRegistrationDTO;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.enums.TenantAccountTypeEnum;
import ai.qorva.core.enums.TenantStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.SubscriptionSyncService;
import ai.qorva.core.service.TenantPurgeService;
import ai.qorva.core.service.UserRegistrationService;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Pattern;

/** Company changes from the admin console. Every change is audited; suspending and deleting end the users' sessions. */
@Service
public class AdminTenantService {

	private final AdminTenantSupport support;
	private final AdminTenantQueries queries;
	private final AdminAuditService audit;
	private final AdminStripeGateway stripe;
	private final AdminProperties properties;
	private final UserRegistrationService registrationService;
	private final SubscriptionSyncService subscriptionSyncService;
	private final TenantPurgeService purgeService;

	public AdminTenantService(AdminTenantSupport support, AdminTenantQueries queries, AdminAuditService audit, AdminStripeGateway stripe,
	                          AdminProperties properties, UserRegistrationService registrationService,
	                          SubscriptionSyncService subscriptionSyncService, TenantPurgeService purgeService) {
		this.support = support;
		this.queries = queries;
		this.audit = audit;
		this.stripe = stripe;
		this.properties = properties;
		this.registrationService = registrationService;
		this.subscriptionSyncService = subscriptionSyncService;
		this.purgeService = purgeService;
	}

	/** A demo account, exactly as the public sign-up creates one (sample data, welcome email with a set-password link). */
	public AdminTenantData.TenantDetail createDemo(AdminTenantData.CreateDemoRequest request) throws QorvaException {
		var email = AdminAuthService.normalize(request.email());
		if (email == null || !email.contains("@") || !StringUtils.hasText(request.firstName()) || !StringUtils.hasText(request.lastName())) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.HTTP_VALIDATION);
		}
		assertEmailFree(support, email);
		var dto = AccountRegistrationDTO.builder()
			.firstName(request.firstName().trim())
			.lastName(request.lastName().trim())
			.email(email)
			.organizationName(request.companyName())
			.organizationSize(request.organizationSize())
			.recruitmentType(request.recruitmentType())
			.languageCode(StringUtils.hasText(request.language()) ? request.language() : "en")
			.build();
		var created = registrationService.createDemoAccount(dto, dto.getLanguageCode());
		audit.record("TENANT_DEMO_CREATED", created.getTenantId(), created.getUserId(), "Demo account for " + email);
		return queries.detail(created.getTenantId());
	}

	public AdminTenantData.TenantDetail updateProfile(String tenantId, AdminTenantData.UpdateTenantRequest request) throws QorvaException {
		var tenant = support.requireTenant(tenantId);
		var update = new Update();
		var changed = new StringBuilder();
		set(update, changed, "tenantName", tenant.getTenantName(), request.tenantName());
		set(update, changed, "contactEmail", tenant.getContactEmail(), request.contactEmail());
		set(update, changed, "phoneNumber", tenant.getPhoneNumber(), request.phoneNumber());
		set(update, changed, "companyAddress", tenant.getCompanyAddress(), request.companyAddress());
		set(update, changed, "websiteUrl", tenant.getWebsiteUrl(), request.websiteUrl());
		set(update, changed, "organizationSize", tenant.getOrganizationSize(), request.organizationSize());
		set(update, changed, "recruitmentType", tenant.getRecruitmentType(), request.recruitmentType());
		if (!changed.isEmpty()) {
			support.updateTenant(tenantId, update, AdminContext.actor());
			audit.record("TENANT_PROFILE_UPDATED", tenantId, null, "Changed: " + changed);
		}
		return queries.detail(tenantId);
	}

	/** ACTIVE or SUSPENDED. A test account is reactivated through the tester route (a new password is required). */
	public AdminTenantData.TenantDetail changeStatus(String tenantId, AdminTenantData.StatusRequest request) throws QorvaException {
		var tenant = support.requireTenant(tenantId);
		var target = parseStatus(request.status());
		var current = TenantStatusEnum.of(tenant.getStatus());
		if (current == TenantStatusEnum.DELETED) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_TENANT_DELETED);
		}
		if (target == TenantStatusEnum.ACTIVE && isTester(tenant)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_USE_TESTER_REACTIVATE);
		}
		if (target != current) {
			applyStatus(tenantId, target, request.reason());
			audit.record(target == TenantStatusEnum.SUSPENDED ? "TENANT_SUSPENDED" : "TENANT_ACTIVATED", tenantId, null,
				current + " → " + target + reasonSuffix(request.reason()));
		}
		return queries.detail(tenantId);
	}

	public AdminTenantData.TenantDetail resyncStripe(String tenantId) throws QorvaException {
		var tenant = support.requireTenant(tenantId);
		var sub = tenant.getSubscriptionInfo();
		if (isTester(tenant) || sub == null || !StringUtils.hasText(sub.getSubscriptionId())) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_NO_STRIPE_SUBSCRIPTION);
		}
		var refreshed = subscriptionSyncService.refreshFromStripe(tenant);
		if (refreshed.isEmpty()) {
			throw QorvaErrors.of(QorvaErrorCodes.ADMIN_STRIPE_FAILED, HttpStatus.SERVICE_UNAVAILABLE);
		}
		audit.record("TENANT_STRIPE_RESYNCED", tenantId, null, "Status " + refreshed.get().getSubscriptionStatus()
			+ ", period end " + refreshed.get().getCurrentPeriodEnd());
		return queries.detail(tenantId);
	}

	/**
	 * Soft delete: access ends now, Stripe stops renewing at the end of the paid period (no refund), and the data is
	 * purged after the grace period. Stripe is told first: if it refuses, nothing is deleted.
	 */
	public AdminTenantData.TenantDetail softDelete(String tenantId, String reason) throws QorvaException {
		var tenant = support.requireTenant(tenantId);
		if (TenantStatusEnum.of(tenant.getStatus()) == TenantStatusEnum.DELETED) {
			return queries.detail(tenantId);
		}
		var sub = tenant.getSubscriptionInfo();
		boolean stripeCancelled = false;
		if (hasLiveSubscription(tenant)) {
			stripe.setCancelAtPeriodEnd(sub.getSubscriptionId(), true);
			stripeCancelled = true;
		}
		var now = Instant.now();
		var purgeAfter = now.atZone(ZoneOffset.UTC).plusMonths(properties.getPurgeGraceMonths()).toInstant();
		var update = statusUpdate(TenantStatusEnum.DELETED, reason).set("deletedAt", now).set("purgeAfter", purgeAfter);
		if (stripeCancelled) {
			update.set("subscriptionInfo.cancelAtPeriodEnd", true);
		}
		support.updateTenant(tenantId, update, AdminContext.actor());
		support.endSessions(tenantId);
		audit.record("TENANT_DELETED", tenantId, null, "Soft-deleted; purge after " + purgeAfter
			+ (stripeCancelled ? "; Stripe cancels at period end" : "") + reasonSuffix(reason));
		return queries.detail(tenantId);
	}

	public AdminTenantData.TenantDetail restore(String tenantId) throws QorvaException {
		var tenant = support.requireTenant(tenantId);
		if (TenantStatusEnum.of(tenant.getStatus()) != TenantStatusEnum.DELETED) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_TENANT_NOT_DELETED);
		}
		if (isTester(tenant)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_USE_TESTER_REACTIVATE);
		}
		if (support.mongo().exists(Query.query(Criteria.where("tenantId").is(new org.bson.types.ObjectId(tenantId))
			.and("type").is(ai.qorva.core.dao.entity.BackgroundJob.TYPE_TENANT_PURGE)
			.and("status").in(ai.qorva.core.dao.entity.BackgroundJob.STATUS_PENDING, ai.qorva.core.dao.entity.BackgroundJob.STATUS_RUNNING)),
			ai.qorva.core.dao.entity.BackgroundJob.class)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_PURGE_IN_PROGRESS);
		}
		boolean resumed = resumeStripeRenewal(tenant);
		var update = statusUpdate(TenantStatusEnum.ACTIVE, null).unset("deletedAt").unset("purgeAfter");
		if (resumed) {
			update.set("subscriptionInfo.cancelAtPeriodEnd", false);
		}
		support.updateTenant(tenantId, update, AdminContext.actor());
		audit.record("TENANT_RESTORED", tenantId, null, "Restored" + (resumed ? "; Stripe renewal resumed" : ""));
		return queries.detail(tenantId);
	}

	public AdminJobData.JobRow purge(String tenantId, String confirmName) throws QorvaException {
		var tenant = support.requireTenant(tenantId);
		if (confirmName == null || !Objects.equals(confirmName.trim(), Objects.requireNonNullElse(tenant.getTenantName(), "").trim())) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_CONFIRM_NAME_MISMATCH);
		}
		var job = purgeService.enqueue(tenantId, AdminContext.actor());
		audit.record("TENANT_PURGE_QUEUED", tenantId, null, "Purge queued (job " + job.getId() + ") for '" + tenant.getTenantName() + "'");
		return AdminJobData.JobRow.from(job, tenant.getTenantName());
	}

	// -------------------------------------------------------------------------

	/** Sets the status and its who/when/why; leaving ACTIVE ends every session of the tenant. */
	void applyStatus(String tenantId, TenantStatusEnum status, String reason) {
		support.updateTenant(tenantId, statusUpdate(status, reason), AdminContext.actor());
		if (status != TenantStatusEnum.ACTIVE) {
			support.endSessions(tenantId);
		}
	}

	static Update statusUpdate(TenantStatusEnum status, String reason) {
		var update = new Update().set("status", status.name()).set("statusChangedAt", Instant.now())
			.set("statusChangedBy", AdminContext.actor());
		return StringUtils.hasText(reason) ? update.set("statusReason", reason.trim()) : update.unset("statusReason");
	}

	/** Undoes the period-end cancellation when the paid period is still running; after it, the customer must subscribe again. */
	boolean resumeStripeRenewal(Tenant tenant) throws QorvaException {
		var sub = tenant.getSubscriptionInfo();
		if (sub == null || !StringUtils.hasText(sub.getSubscriptionId()) || !Boolean.TRUE.equals(sub.getCancelAtPeriodEnd())
			|| sub.getCurrentPeriodEnd() == null || !sub.getCurrentPeriodEnd().isAfter(Instant.now())) {
			return false;
		}
		stripe.setCancelAtPeriodEnd(sub.getSubscriptionId(), false);
		return true;
	}

	private static boolean hasLiveSubscription(Tenant tenant) {
		var sub = tenant.getSubscriptionInfo();
		return !isTester(tenant) && sub != null && StringUtils.hasText(sub.getSubscriptionId())
			&& !SubscriptionStatus.CANCELED.getValue().equals(sub.getSubscriptionStatus())
			&& !Boolean.TRUE.equals(sub.getCancelAtPeriodEnd());
	}

	static boolean isTester(Tenant tenant) {
		return TenantAccountTypeEnum.TESTER.name().equals(tenant.getAccountType());
	}

	static void assertEmailFree(AdminTenantSupport support, String email) throws QorvaException {
		var query = Query.query(Criteria.where("email").regex("^" + Pattern.quote(email) + "$", "i"));
		if (support.mongo().exists(query, User.class)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_EMAIL_IN_USE);
		}
	}

	private static TenantStatusEnum parseStatus(String status) throws QorvaException {
		if ("ACTIVE".equals(status)) return TenantStatusEnum.ACTIVE;
		if ("SUSPENDED".equals(status)) return TenantStatusEnum.SUSPENDED;
		throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_INVALID_STATUS, status);
	}

	private static void set(Update update, StringBuilder changed, String field, String current, String requested) throws QorvaException {
		if (requested == null || Objects.equals(current, requested.trim())) {
			return;
		}
		if ("tenantName".equals(field) && requested.isBlank()) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.HTTP_VALIDATION);
		}
		update.set(field, requested.trim());
		changed.append(changed.isEmpty() ? "" : ", ").append(field);
	}

	static String reasonSuffix(String reason) {
		return StringUtils.hasText(reason) ? " — " + reason.trim() : "";
	}
}
