package ai.qorva.core.service;

import ai.qorva.core.config.StripeProperties;
import ai.qorva.core.dto.*;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.SubscriptionStatus;
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
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class UserRegistrationService {

	private final UserService userService;
	private final TenantService tenantService;
	private final ProductReferenceService productReferenceService;
	private final AccountRegistrationMapper accountRegistrationMapper;
	private final StripeProperties stripeProperties;

	@Autowired
	public UserRegistrationService(
		UserService userService,
		TenantService tenantService,
		ProductReferenceService productReferenceService,
		AccountRegistrationMapper accountRegistrationMapper,
		StripeProperties stripeProperties
	) {
		this.userService = userService;
		this.tenantService = tenantService;
		this.productReferenceService = productReferenceService;
		this.accountRegistrationMapper = accountRegistrationMapper;
		this.stripeProperties = stripeProperties;
	}

	@PostConstruct
	public void init() {
		Stripe.apiKey = stripeProperties.getSecretKey();
	}

	/**
	 * Steps 2-5: validate → create pending tenant+user → create Stripe customer → create checkout session.
	 * Returns the checkout URL the frontend must redirect the user to.
	 */
	public RegistrationResponseDTO createAccount(AccountRegistrationDTO dto, String languageCode) throws QorvaException {
		log.info("Starting registration for user: {} – language: {}", dto.getEmail(), languageCode);

		// Step 2: validate priceId against known product references
		var product = resolveProductByPriceId(dto.getPriceId());

		// Step 3: create tenant with PENDING_SUBSCRIPTION status
		var tenant = createCompanyInfo(dto, languageCode, product, dto.getPriceId());

		// Step 3: create user as PENDING_SUBSCRIPTION
		var user = createNewUser(dto, tenant);

		// Step 4: create Stripe customer and update tenant with stripeCustomerId
		var stripeCustomer = createStripeCustomer(dto, tenant.getId());
		tenant.setStripeCustomerId(stripeCustomer.getId());
		tenantService.updateOne(tenant.getId(), tenant);
		log.info("Stripe customer created: {} for tenant: {}", stripeCustomer.getId(), tenant.getId());

		// Step 5: create Stripe checkout session
		var checkoutUrl = createCheckoutSession(dto.getPriceId(), stripeCustomer.getId(), tenant.getId(), user.getId());
		log.info("Checkout session created for tenant: {}", tenant.getId());

		return new RegistrationResponseDTO(checkoutUrl, tenant.getId(), user.getId());
	}

	public RegistrationResponseDTO renewCheckoutSession(CheckoutSessionRequestDTO dto) throws QorvaException {
		log.info("Renewing checkout session for tenant: {} user: {}", dto.getTenantId(), dto.getUserId());

		resolveProductByPriceId(dto.getPriceId());

		var tenant = tenantService.findOneById(dto.getTenantId());
		if (tenant == null || !StringUtils.hasText(tenant.getStripeCustomerId())) {
			throw new QorvaException(QorvaErrorCodes.BILLING_NO_STRIPE_CUSTOMER);
		}

		var checkoutUrl = createCheckoutSession(dto.getPriceId(), tenant.getStripeCustomerId(), dto.getTenantId(), dto.getUserId());
		log.info("New checkout session created for tenant: {}", dto.getTenantId());

		return new RegistrationResponseDTO(checkoutUrl, dto.getTenantId(), dto.getUserId());
	}

	private ProductReferenceDTO resolveProductByPriceId(String priceId) throws QorvaException {
		if (!StringUtils.hasText(priceId)) {
			throw new QorvaException(QorvaErrorCodes.BILLING_PRICE_ID_REQUIRED);
		}
		var product = Optional.ofNullable(productReferenceService.findByStripePriceId(priceId))
			   .orElseThrow(() -> new QorvaException("Product not found for priceId: " + priceId));

		log.info("Validated product {} for priceId: {}", product.getName(), priceId);
		return product;
	}

	private Customer createStripeCustomer(AccountRegistrationDTO dto, String tenantId) throws QorvaException {
		try {
			CustomerCreateParams params = CustomerCreateParams.builder()
				.setEmail(dto.getEmail())
				.setName(dto.getFirstName() + " " + dto.getLastName())
				.putMetadata("tenantId", tenantId)
				.build();
			return Customer.create(params);
		} catch (StripeException e) {
			log.error("Failed to create Stripe customer for email {}", dto.getEmail(), e);
			throw new QorvaException(QorvaErrorCodes.BILLING_CUSTOMER_CREATION_FAILED, e);
		}
	}

	private String createCheckoutSession(String priceId, String stripeCustomerId, String tenantId, String userId) throws QorvaException {
		try {
			SessionCreateParams params = SessionCreateParams.builder()
				.setMode(SessionCreateParams.Mode.SUBSCRIPTION)
				.setCustomer(stripeCustomerId)
				.addLineItem(SessionCreateParams.LineItem.builder()
					.setPrice(priceId)
					.setQuantity(1L)
					.build())
				.setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
					.setTrialPeriodDays(7L)
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

	protected UserDTO createNewUser(AccountRegistrationDTO newAccountDTO, TenantDTO companyInfo) throws QorvaException {
		var userDTO = accountRegistrationMapper.map(newAccountDTO);
		userDTO.setUserAccountStatus(UserStatusEnum.PENDING_SUBSCRIPTION.getValue());
		userDTO.setTenantId(companyInfo.getId());
		userDTO.setCommunicationLanguage(newAccountDTO.getLanguageCode());
		userDTO.setAuthorities(UserAuthoritiesHelper.createAuthorities());
		return userService.createOne(userDTO);
	}

	protected TenantDTO createCompanyInfo(AccountRegistrationDTO newAccountDTO, String languageCode, ProductReferenceDTO product, String priceId) throws QorvaException {
		newAccountDTO.setLanguageCode(languageCode);

		final String companyName = StringUtils.hasText(newAccountDTO.getCompanyName())
			? newAccountDTO.getCompanyName()
			: newAccountDTO.getFirstName() + " " + newAccountDTO.getLastName();

		var subscriptionInfo = new SubscriptionInfo();
		subscriptionInfo.setSubscriptionStatus(SubscriptionStatus.PENDING_SUBSCRIPTION.getValue());
		subscriptionInfo.setSubscriptionPlan(product.getName());
		subscriptionInfo.setPriceId(priceId);
		subscriptionInfo.setPlanCode(priceId);

		if (product.getPrices() != null) {
			product.getPrices().stream()
				.filter(p -> priceId.equals(p.getStripePriceId()))
				.findFirst()
				.ifPresent(p -> {
					subscriptionInfo.setBillingCycle(p.getInterval());
					if (p.getUnitAmount() != null) {
						subscriptionInfo.setPrice(new Decimal128(p.getUnitAmount()));
					}
				});
		}

		var tenantDTO = new TenantDTO();
		tenantDTO.setTenantName(companyName);
		tenantDTO.setOrganizationId("Q-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
		tenantDTO.setSubscriptionInfo(subscriptionInfo);

		return tenantService.createOne(tenantDTO);
	}
}
