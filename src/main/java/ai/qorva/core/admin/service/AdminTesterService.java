package ai.qorva.core.admin.service;

import ai.qorva.core.admin.dto.AdminTesterData;
import ai.qorva.core.admin.security.AdminContext;
import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.entity.UsageMonitoring;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.enums.TenantAccountTypeEnum;
import ai.qorva.core.enums.TenantStatusEnum;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.helpers.UserAuthoritiesHelper;
import ai.qorva.core.scheduler.UsageMonitoringScheduler;
import ai.qorva.core.security.TenantScope;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Test accounts: a company and its owner created in one go with the admin's password, a tier (the product's monthly
 * price, so the tier's limits apply without any Stripe subscription) and an access end. No email is sent.
 * Going from unusable (deactivated, deleted, expired) back to usable always replaces the owner's password.
 */
@Slf4j
@Service
public class AdminTesterService {

	private final AdminTenantSupport support;
	private final AdminTesterQueries queries;
	private final AdminAuditService audit;
	private final TenantService tenantService;
	private final UserService userService;
	private final UsageMonitoringService usageMonitoringService;
	private final PasswordEncoder passwordEncoder;

	public AdminTesterService(AdminTenantSupport support, AdminTesterQueries queries, AdminAuditService audit, TenantService tenantService,
	                          UserService userService, UsageMonitoringService usageMonitoringService, PasswordEncoder passwordEncoder) {
		this.support = support;
		this.queries = queries;
		this.audit = audit;
		this.tenantService = tenantService;
		this.userService = userService;
		this.usageMonitoringService = usageMonitoringService;
		this.passwordEncoder = passwordEncoder;
	}

	public AdminTesterData.Tester create(AdminTesterData.CreateRequest request) throws QorvaException {
		var email = AdminAuthService.normalize(request.email());
		if (email == null || !email.contains("@") || !StringUtils.hasText(request.firstName()) || !StringUtils.hasText(request.lastName())) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.HTTP_VALIDATION);
		}
		AdminAuthService.requireStrong(request.password());
		requireFuture(request.accessExpiresAt());
		var tier = queries.resolve(request.productId());
		AdminTenantService.assertEmailFree(support, email);

		var now = Instant.now();
		var periodEnd = UsageMonitoringScheduler.testerPeriodEnd(now, request.accessExpiresAt());
		var name = StringUtils.hasText(request.companyName()) ? request.companyName().trim()
			: request.firstName().trim() + " " + request.lastName().trim() + " (tester)";

		var tenantDTO = new TenantDTO();
		tenantDTO.setTenantName(name);
		tenantDTO.setOrganizationId("Q-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
		tenantDTO.setContactEmail(email);
		tenantDTO.setSubscriptionInfo(subscription(tier, now, periodEnd));
		tenantDTO.setStatus(TenantStatusEnum.ACTIVE.name());
		tenantDTO.setAccountType(TenantAccountTypeEnum.TESTER.name());
		tenantDTO.setInternal(true);
		tenantDTO.setAccessExpiresAt(request.accessExpiresAt());
		var tenant = TenantScope.callAsSystem("admin: create tester", () -> tenantService.createOne(tenantDTO));
		try {
			var user = TenantScope.callAs(tenant.getId(), () -> userService.createOne(owner(tenant.getId(), request, email)));
			startUsagePeriod(tenant.getId(), tier, now, periodEnd);
			audit.record("TESTER_CREATED", tenant.getId(), user.getId(), email + ", tier " + tier.product().getName()
				+ ", access until " + request.accessExpiresAt());
		} catch (QorvaException | RuntimeException e) {
			log.warn("Tester creation failed for {} — removing tenant {}", email, tenant.getId(), e);
			removeHalfCreated(tenant.getId());
			throw e;
		}
		return queries.get(tenant.getId());
	}

	/** Only while the tester is usable; ending access or re-opening it goes through deactivate / reactivate. */
	public AdminTesterData.Tester changeExpiry(String tenantId, Instant accessExpiresAt) throws QorvaException {
		var tenant = requireTester(tenantId);
		if (!usable(tenant)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_USE_TESTER_REACTIVATE);
		}
		requireFuture(accessExpiresAt);
		var previous = tenant.getAccessExpiresAt();
		support.updateTenant(tenantId, new Update().set("accessExpiresAt", accessExpiresAt), AdminContext.actor());
		// Tokens are capped at the old end: an earlier end must cut them now; a later one lets them refresh into it.
		if (previous == null || accessExpiresAt.isBefore(previous)) {
			support.endSessions(tenantId);
		}
		audit.record("TESTER_EXPIRY_CHANGED", tenantId, null, "Access until " + previous + " → " + accessExpiresAt);
		return queries.get(tenantId);
	}

	public AdminTesterData.Tester changeTier(String tenantId, String productId) throws QorvaException {
		var tenant = requireTester(tenantId);
		var tier = queries.resolve(productId);
		var previous = tenant.getSubscriptionInfo() != null ? tenant.getSubscriptionInfo().getSubscriptionPlan() : null;
		support.updateTenant(tenantId, new Update()
			.set("subscriptionInfo.subscriptionPlan", tier.product().getName())
			.set("subscriptionInfo.priceId", tier.priceId())
			.set("subscriptionInfo.planCode", tier.priceId()), AdminContext.actor());
		applyLimitsToCurrentPeriod(tenantId, tier);
		// The plan travels in the JWT: sign in again to get the new one.
		support.endSessions(tenantId);
		audit.record("TESTER_TIER_CHANGED", tenantId, null, previous + " → " + tier.product().getName());
		return queries.get(tenantId);
	}

	public AdminTesterData.Tester deactivate(String tenantId, String reason) throws QorvaException {
		var tenant = requireTester(tenantId);
		if (TenantStatusEnum.of(tenant.getStatus()) == TenantStatusEnum.DELETED) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_TENANT_DELETED);
		}
		support.updateTenant(tenantId, AdminTenantService.statusUpdate(TenantStatusEnum.SUSPENDED, reason), AdminContext.actor());
		support.endSessions(tenantId);
		audit.record("TESTER_DEACTIVATED", tenantId, null, "Deactivated" + AdminTenantService.reasonSuffix(reason));
		return queries.get(tenantId);
	}

	/** Back to usable, always with a new password for the tester; a new end date when the old one has passed. */
	public AdminTesterData.Tester reactivate(String tenantId, AdminTesterData.ReactivateRequest request) throws QorvaException {
		var tenant = requireTester(tenantId);
		if (!StringUtils.hasText(request.password())) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_PASSWORD_REQUIRED);
		}
		AdminAuthService.requireStrong(request.password());
		var expiry = request.accessExpiresAt() != null ? request.accessExpiresAt() : tenant.getAccessExpiresAt();
		if (request.accessExpiresAt() == null && (expiry == null || !expiry.isAfter(Instant.now()))) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_EXPIRY_REQUIRED);
		}
		requireFuture(expiry);
		if (TenantStatusEnum.of(tenant.getStatus()) == TenantStatusEnum.DELETED && purgeRunning(tenantId)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_PURGE_IN_PROGRESS);
		}
		var owner = AdminTesterQueries.owner(support, tenantId);
		if (owner.getEncryptedPassword() != null && passwordEncoder.matches(request.password(), owner.getEncryptedPassword())) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_PASSWORD_UNCHANGED);
		}
		var update = AdminTenantService.statusUpdate(TenantStatusEnum.ACTIVE, null).set("accessExpiresAt", expiry)
			.unset("deletedAt").unset("purgeAfter");
		support.updateTenant(tenantId, update, AdminContext.actor());
		setPassword(owner, request.password());
		support.endSessions(tenantId);
		ensureUsagePeriod(tenantId, expiry);
		audit.record("TESTER_REACTIVATED", tenantId, owner.getId(), "Reactivated with a new password; access until " + expiry);
		return queries.get(tenantId);
	}

	public AdminTesterData.Tester resetPassword(String tenantId, String password) throws QorvaException {
		requireTester(tenantId);
		AdminAuthService.requireStrong(password);
		var owner = AdminTesterQueries.owner(support, tenantId);
		setPassword(owner, password);
		audit.record("TESTER_PASSWORD_RESET", tenantId, owner.getId(), "Password replaced by an admin");
		return queries.get(tenantId);
	}

	// -------------------------------------------------------------------------

	private Tenant requireTester(String tenantId) throws QorvaException {
		var tenant = support.requireTenant(tenantId);
		if (!AdminTenantService.isTester(tenant)) {
			throw QorvaErrors.conflict(QorvaErrorCodes.ADMIN_NOT_A_TESTER);
		}
		return tenant;
	}

	private boolean purgeRunning(String tenantId) {
		return support.mongo().exists(Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
			.and("type").is(ai.qorva.core.dao.entity.BackgroundJob.TYPE_TENANT_PURGE)
			.and("status").in(ai.qorva.core.dao.entity.BackgroundJob.STATUS_PENDING, ai.qorva.core.dao.entity.BackgroundJob.STATUS_RUNNING)),
			ai.qorva.core.dao.entity.BackgroundJob.class);
	}

	private static boolean usable(Tenant tenant) {
		return ai.qorva.core.service.TenantAccess.refusal(tenant).isEmpty();
	}

	private static void requireFuture(Instant instant) throws QorvaException {
		if (instant == null || !instant.isAfter(Instant.now())) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.ADMIN_EXPIRY_IN_PAST);
		}
	}

	/** New password, and the credential version moves on: every session and link of the owner ends. */
	private void setPassword(User owner, String password) {
		support.mongo().updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(owner.getId()))),
			new Update().set("encryptedPassword", passwordEncoder.encode(password)).inc("passwordCredentialVersion", 1)
				.set("userAccountStatus", UserStatusEnum.ACTIVE.getValue()).set("invitePending", false), User.class);
	}

	private static SubscriptionInfo subscription(AdminTesterQueries.ResolvedTier tier, Instant start, Instant end) {
		var info = new SubscriptionInfo();
		info.setSubscriptionPlan(tier.product().getName());
		info.setPriceId(tier.priceId());
		info.setPlanCode(tier.priceId());
		info.setBillingCycle("month");
		info.setSubscriptionStatus(SubscriptionStatus.ACTIVE.getValue());
		info.setSubscriptionStartDate(start);
		info.setCurrentPeriodStart(start);
		info.setCurrentPeriodEnd(end);
		return info;
	}

	private static UserDTO owner(String tenantId, AdminTesterData.CreateRequest request, String email) {
		var user = new UserDTO();
		user.setTenantId(tenantId);
		user.setFirstName(request.firstName().trim());
		user.setLastName(request.lastName().trim());
		user.setEmail(email);
		user.setRawPassword(request.password());
		user.setUserAccountStatus(UserStatusEnum.ACTIVE.getValue());
		user.setCommunicationLanguage(StringUtils.hasText(request.language()) ? request.language() : "en");
		user.setPasswordCredentialVersion(0);
		user.setInvitePending(false);
		user.setAuthorities(UserAuthoritiesHelper.createAuthorities());
		return user;
	}

	private void startUsagePeriod(String tenantId, AdminTesterQueries.ResolvedTier tier, Instant start, Instant end) throws QorvaException {
		ProductFeatures features = tier.product().getFeatures();
		TenantScope.callAs(tenantId, () -> usageMonitoringService.initializePeriod(tenantId, tier.product().getName(), start, end, features));
	}

	/** A reactivated tester gets a usage period at once instead of waiting for the scheduler. */
	private void ensureUsagePeriod(String tenantId, Instant accessExpiresAt) throws QorvaException {
		if (TenantScope.callAs(tenantId, () -> usageMonitoringService.findCurrentPeriodByTenantId(tenantId)).isPresent()) {
			return;
		}
		var tenant = support.requireTenant(tenantId);
		var sub = tenant.getSubscriptionInfo();
		if (sub == null || !StringUtils.hasText(sub.getPriceId())) {
			return;
		}
		var now = Instant.now();
		var end = UsageMonitoringScheduler.testerPeriodEnd(now, accessExpiresAt);
		support.updateTenant(tenantId, new Update().set("subscriptionInfo.currentPeriodStart", now)
			.set("subscriptionInfo.currentPeriodEnd", end), AdminContext.actor());
		var product = queries.resolveByPrice(sub.getPriceId());
		startUsagePeriod(tenantId, product, now, end);
	}

	/** The tier's limits on the running period; what was consumed stays. */
	private void applyLimitsToCurrentPeriod(String tenantId, AdminTesterQueries.ResolvedTier tier) {
		var limits = tier.product().getFeatures() != null ? tier.product().getFeatures().getLimits() : null;
		var now = Instant.now();
		var update = new Update().set("subscriptionTier", tier.product().getName());
		if (limits != null) {
			update.set("features.screeningActions.limit", limits.getScreeningActions())
				.set("features.aiResumeChats.limit", limits.getAiResumeChats())
				.set("features.talentIntelligenceQueries.limit", limits.getTalentIntelligenceQueries())
				.set("features.agentRuns.limit", limits.getAgentRuns());
		}
		support.mongo().updateMulti(Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId))
			.and("currentPeriodStart").lte(now).and("currentPeriodEnd").gt(now)), update, UsageMonitoring.class);
	}

	private void removeHalfCreated(String tenantId) {
		var oid = new ObjectId(tenantId);
		support.mongo().remove(Query.query(Criteria.where("tenantId").is(oid)), User.class);
		support.mongo().remove(Query.query(Criteria.where("tenantId").is(oid)), UsageMonitoring.class);
		support.mongo().remove(Query.query(Criteria.where("_id").is(oid)), Tenant.class);
	}
}
