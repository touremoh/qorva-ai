package ai.qorva.core.service;

import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.config.StripeProperties;
import ai.qorva.core.dto.*;
import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.helpers.UserAuthoritiesHelper;
import ai.qorva.core.mapper.AccountRegistrationMapper;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class UserRegistrationService {

	/** Long-lived usage period for demo tenants (no billing cycle to reset against). */
	private static final long DEMO_USAGE_PERIOD_DAYS = 365L;

	/** Free-trial length granted when a demo user adds a card and upgrades. */
	private static final long UPGRADE_TRIAL_DAYS = 14L;

	private final UserService userService;
	private final TenantService tenantService;
	private final ProductReferenceService productReferenceService;
	private final AccountRegistrationMapper accountRegistrationMapper;
	private final StripeProperties stripeProperties;
	private final SetPasswordService setPasswordService;
	private final DemoSeedService demoSeedService;
	private final UsageMonitoringService usageMonitoringService;
	private final QorvaProductProperties qorvaProductProperties;

	@Autowired
	public UserRegistrationService(
		UserService userService,
		TenantService tenantService,
		ProductReferenceService productReferenceService,
		AccountRegistrationMapper accountRegistrationMapper,
		StripeProperties stripeProperties,
		SetPasswordService setPasswordService,
		DemoSeedService demoSeedService,
		UsageMonitoringService usageMonitoringService,
		QorvaProductProperties qorvaProductProperties
	) {
		this.userService = userService;
		this.tenantService = tenantService;
		this.productReferenceService = productReferenceService;
		this.accountRegistrationMapper = accountRegistrationMapper;
		this.stripeProperties = stripeProperties;
		this.setPasswordService = setPasswordService;
		this.demoSeedService = demoSeedService;
		this.usageMonitoringService = usageMonitoringService;
		this.qorvaProductProperties = qorvaProductProperties;
	}

	@PostConstruct
	public void init() {
		Stripe.apiKey = stripeProperties.getSecretKey();
	}

	/**
	 * Creates a demo account: tenant (no plan) + user (status DEMO, restricted authorities, no usable
	 * password yet), seeds a bounded usage quota and sample data, and emails a set-password link.
	 * No Stripe interaction and no checkout URL — the frontend shows a "check your email" success page.
	 */
	public DemoRegistrationResponseDTO createDemoAccount(AccountRegistrationDTO dto, String languageCode) throws QorvaException {
		log.info("Starting demo registration for user: {} – language: {}", dto.getEmail(), languageCode);
		dto.setLanguageCode(languageCode);

		// Guard: detect re-registration before creating any new resources
		var existingUser = userService.findByEmail(dto.getEmail());
		if (existingUser != null) {
			if (UserStatusEnum.DEMO.getValue().equals(existingUser.getUserAccountStatus())) {
				// Idempotent re-registration of a demo account: just re-send the set-password link
				log.info("Re-registration of demo user {} – re-sending set-password link", existingUser.getId());
				setPasswordService.enqueueDemoWelcome(existingUser.getId());
				return new DemoRegistrationResponseDTO(true, dto.getEmail(), existingUser.getTenantId(), existingUser.getId());
			}
			throw new QorvaException(QorvaErrorCodes.USER_ALREADY_EXISTS, HttpStatus.NOT_ACCEPTABLE.value(), HttpStatus.NOT_ACCEPTABLE);
		}

		TenantDTO tenant = null;
		UserDTO user = null;
		try {
			tenant = createDemoTenant(dto);
			user = createDemoUser(dto, tenant);

			// Bounded matching-report quota using the smallest paid tier (Starter) limits
			seedDemoUsageMonitoring(tenant.getId());

			// Email the set-password link (best-effort, queued + retried by the scheduler)
			setPasswordService.enqueueDemoWelcome(user.getId());

			// Seed sample data (best-effort; must not roll back account creation)
			demoSeedService.seed(tenant.getId(), dto.getRecruitmentType(), languageCode);

			log.info("Demo account created: tenant={} user={}", tenant.getId(), user.getId());
			return new DemoRegistrationResponseDTO(true, dto.getEmail(), tenant.getId(), user.getId());
		} catch (QorvaException e) {
			cleanupFailedRegistration(tenant, user);
			throw e;
		} catch (Exception e) {
			cleanupFailedRegistration(tenant, user);
			throw new QorvaException("Demo registration failed", e);
		}
	}

	/**
	 * Converts a demo account to a paid subscription with a 14-day free trial. The user picks the plan
	 * ({@code priceId}). Returns the Stripe checkout URL; activation (purge of demo data, authority
	 * upgrade, status flip) happens on the checkout.session.completed webhook.
	 */
	public RegistrationResponseDTO upgrade(String tenantId, String userId, String priceId) throws QorvaException {
		log.info("Upgrade requested: tenant={} user={} priceId={}", tenantId, userId, priceId);
		resolveProductByPriceId(priceId);

		var tenant = tenantService.findOneById(tenantId);

		// Tenant context is present (authenticated demo user); findOneById enforces ownership.
		var user = userService.findOneById(userId);

		String stripeCustomerId = tenant.getStripeCustomerId();
		if (!StringUtils.hasText(stripeCustomerId)) {
			var customer = createStripeCustomer(user.getEmail(), user.getFirstName() + " " + user.getLastName(), tenantId);
			stripeCustomerId = customer.getId();
			tenant.setStripeCustomerId(stripeCustomerId);
			tenantService.updateOne(tenantId, tenant);
			log.info("Stripe customer created for upgrade: {} for tenant {}", stripeCustomerId, tenantId);
		}

		var checkoutUrl = createCheckoutSession(priceId, stripeCustomerId, tenantId, userId, UPGRADE_TRIAL_DAYS);
		log.info("Upgrade checkout session created for tenant {}", tenantId);
		return new RegistrationResponseDTO(checkoutUrl, tenantId, userId);
	}

	public RegistrationResponseDTO renewCheckoutSession(CheckoutSessionRequestDTO dto) throws QorvaException {
		log.info("Renewing checkout session for tenant: {} user: {}", dto.getTenantId(), dto.getUserId());

		resolveProductByPriceId(dto.getPriceId());

		var tenant = tenantService.findOneById(dto.getTenantId());
		if (tenant == null || !StringUtils.hasText(tenant.getStripeCustomerId())) {
			throw new QorvaException(QorvaErrorCodes.BILLING_NO_STRIPE_CUSTOMER);
		}

		var checkoutUrl = createCheckoutSession(dto.getPriceId(), tenant.getStripeCustomerId(), dto.getTenantId(), dto.getUserId(), UPGRADE_TRIAL_DAYS);
		log.info("New checkout session created for tenant: {}", dto.getTenantId());

		return new RegistrationResponseDTO(checkoutUrl, dto.getTenantId(), dto.getUserId());
	}

	// -------------------------------------------------------------------------
	// Demo account building blocks
	// -------------------------------------------------------------------------

	protected TenantDTO createDemoTenant(AccountRegistrationDTO dto) throws QorvaException {
		final String companyName = StringUtils.hasText(dto.getOrganizationName())
			? dto.getOrganizationName()
			: dto.getFirstName() + " " + dto.getLastName();

		var tenantDTO = new TenantDTO();
		tenantDTO.setTenantName(companyName);
		tenantDTO.setOrganizationId("Q-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
		tenantDTO.setRecruitmentType(dto.getRecruitmentType());
		tenantDTO.setOrganizationSize(dto.getOrganizationSize());
		// Empty (no plan) subscription info — TenantService requires it to be non-null.
		tenantDTO.setSubscriptionInfo(new SubscriptionInfo());

		return tenantService.createOne(tenantDTO);
	}

	protected UserDTO createDemoUser(AccountRegistrationDTO dto, TenantDTO tenant) throws QorvaException {
		var userDTO = accountRegistrationMapper.map(dto);
		userDTO.setUserAccountStatus(UserStatusEnum.DEMO.getValue());
		userDTO.setTenantId(tenant.getId());
		userDTO.setCommunicationLanguage(dto.getLanguageCode());
		userDTO.setPasswordCredentialVersion(0);
		// Unusable placeholder — the real password is chosen via the set-password link.
		// Must stay under bcrypt's 72-byte input limit.
		userDTO.setRawPassword(UUID.randomUUID().toString());
		userDTO.setAuthorities(UserAuthoritiesHelper.createDemoAuthorities());
		return userService.createOne(userDTO);
	}

	private void seedDemoUsageMonitoring(String tenantId) {
		try {
			ProductFeatures starterFeatures = qorvaProductProperties.getStarter() != null
				? qorvaProductProperties.getStarter().getFeatures()
				: null;
			var now = Instant.now();
			usageMonitoringService.initializePeriod(
				tenantId,
				"Starter",
				now,
				now.plus(DEMO_USAGE_PERIOD_DAYS, ChronoUnit.DAYS),
				starterFeatures
			);
		} catch (Exception e) {
			log.warn("Failed to seed demo usage monitoring for tenant={} – report quota will be unbounded", tenantId, e);
		}
	}

	private void cleanupFailedRegistration(TenantDTO tenant, UserDTO user) {
		if (tenant == null) {
			return;
		}
		log.warn("Registration failed – initiating cleanup for tenant: {}", tenant.getId());

		if (user != null) {
			try {
				userService.deleteOneById(user.getId(), user.getTenantId());
				log.info("Cleaned up user {} during failed registration", user.getId());
			} catch (Exception ex) {
				log.error("Failed to delete user {} during registration cleanup", user.getId(), ex);
			}
		}

		try {
			long remainingUsers = userService.countAll(tenant.getId());
			if (remainingUsers == 0) {
				tenantService.deleteOneById(tenant.getId(), tenant.getId());
				log.info("Cleaned up tenant {} during failed registration", tenant.getId());
			} else {
				log.warn("Skipping tenant {} deletion – {} user(s) still registered", tenant.getId(), remainingUsers);
			}
		} catch (Exception ex) {
			log.error("Failed to delete tenant {} during registration cleanup", tenant.getId(), ex);
		}
	}

	// -------------------------------------------------------------------------
	// Stripe helpers (shared by upgrade / renew)
	// -------------------------------------------------------------------------

	private ProductReferenceDTO resolveProductByPriceId(String priceId) throws QorvaException {
		if (!StringUtils.hasText(priceId)) {
			throw new QorvaException(QorvaErrorCodes.BILLING_PRICE_ID_REQUIRED);
		}
		var product = Optional.ofNullable(productReferenceService.findByStripePriceId(priceId))
			   .orElseThrow(() -> new QorvaException("Product not found for priceId: " + priceId));

		log.info("Validated product {} for priceId: {}", product.getName(), priceId);
		return product;
	}

	private Customer createStripeCustomer(String email, String name, String tenantId) throws QorvaException {
		try {
			CustomerCreateParams params = CustomerCreateParams.builder()
				.setEmail(email)
				.setName(name)
				.putMetadata("tenantId", tenantId)
				.build();
			return Customer.create(params);
		} catch (StripeException e) {
			log.error("Failed to create Stripe customer for email {}", email, e);
			throw new QorvaException(QorvaErrorCodes.BILLING_CUSTOMER_CREATION_FAILED, e);
		}
	}

	private String createCheckoutSession(String priceId, String stripeCustomerId, String tenantId, String userId, long trialDays) throws QorvaException {
		try {
			SessionCreateParams params = SessionCreateParams.builder()
				.setMode(SessionCreateParams.Mode.SUBSCRIPTION)
				.setCustomer(stripeCustomerId)
				.setAllowPromotionCodes(true)
				.addLineItem(SessionCreateParams.LineItem.builder()
					.setPrice(priceId)
					.setQuantity(1L)
					.build())
				.setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
					.setTrialPeriodDays(trialDays)
					.build())
				.setSuccessUrl(stripeProperties.getCheckout().getSuccessUrl())
				.setCancelUrl(stripeProperties.getCheckout().getCancelUrl())
				.setClientReferenceId(tenantId)
				.putMetadata("userId", userId)
				.build();
			return Session.create(params).getUrl();
		} catch (StripeException e) {
			log.error("Failed to create Stripe checkout session for tenant {}", tenantId, e);
			throw new QorvaException(QorvaErrorCodes.BILLING_CHECKOUT_FAILED, e);
		}
	}
}
