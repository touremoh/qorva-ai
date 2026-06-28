package ai.qorva.core.service;

import ai.qorva.core.config.StripeProperties;
import ai.qorva.core.dao.entity.StripeEventLog;
import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.*;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.dao.querybuilder.StripeEventLogQueryBuilder;
import ai.qorva.core.service.handlers.*;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.SetupIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.net.ApiResource;
import com.stripe.param.billingportal.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class StripeEventsService extends AbstractQorvaService<StripeEventLogDTO, StripeEventLog> {

	protected static final String CUSTOMER_SUBSCRIPTION_CREATED = "customer.subscription.created";
	protected static final String CUSTOMER_SUBSCRIPTION_UPDATED = "customer.subscription.updated";
	protected static final String CUSTOMER_SUBSCRIPTION_DELETED = "customer.subscription.deleted";
	protected static final String CUSTOMER_SUBSCRIPTION_PAUSED = "customer.subscription.paused";
	protected static final String CUSTOMER_SUBSCRIPTION_RESUMED = "customer.subscription.resumed";
	protected static final String CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";
	protected static final String INVOICE_PAYMENT_FAILED = "invoice.payment_failed";
	protected static final String INVOICE_PAYMENT_SUCCEEDED = "invoice.payment_succeeded";
	protected static final String INVOICE_PAID = "invoice.paid";
	protected static final String INVOICE_FINALIZED = "invoice.finalized";
	protected static final String INVOICE_CREATED = "invoice.created";
	protected static final String SETUP_INTENT_CREATED = "setup_intent.created";
	protected static final String SETUP_INTENT_SUCCEEDED = "setup_intent.succeeded";
	protected static final String CUSTOMER_CREATED = "customer.created";
	protected static final String CUSTOMER_UPDATED = "customer.updated";
	protected static final String CUSTOMER_DELETED = "customer.deleted";
	protected static final String PAYMENT_METHOD_ATTACHED = "payment_method.attached";
	protected static final String PRODUCT_CREATED = "product.created";
	protected static final String PRODUCT_UPDATED = "product.updated";
	protected static final String PRICE_CREATED = "price.created";
	protected static final String PRICE_UPDATED = "price.updated";

	protected final StripeProperties stripeProperties;

	protected final StripeCheckoutSessionCompletedHandler checkoutSessionHandler;
	protected final StripeSubscriptionCreatedHandler subscriptionCreatedHandler;
	protected final StripeSubscriptionUpdatedHandler subscriptionUpdatedHandler;
	protected final StripeSubscriptionDeletedHandler subscriptionDeletedHandler;
	protected final StripeSubscriptionPausedHandler subscriptionPausedHandler;
	protected final StripeSubscriptionResumedHandler subscriptionResumedHandler;
	protected final StripeInvoicePaymentFailedHandler invoicePaymentFailedHandler;
	protected final StripeInvoicePaymentSucceededHandler invoicePaymentSucceededHandler;
	protected final StripeInvoicePaidHandler invoicePaidHandler;
	protected final StripeInvoiceFinalizedHandler invoiceFinalizedHandler;
	protected final StripeInvoiceCreatedHandler invoiceCreatedHandler;
	protected final StripeSetupIntentCreatedHandler setupIntentCreatedHandler;
	protected final StripeSetupIntentSucceededHandler setupIntentSucceededHandler;
	protected final StripeCustomerCreatedHandler customerCreatedHandler;
	protected final StripeCustomerUpdatedHandler customerUpdatedHandler;
	protected final StripeCustomerDeletedHandler customerDeletedHandler;
	protected final StripePaymentMethodAttachedHandler paymentMethodAttachedHandler;
	protected final StripeProductCreatedHandler productCreatedHandler;
	protected final StripeProductUpdatedHandler productUpdatedHandler;
	protected final StripePriceCreatedHandler priceCreatedHandler;
	protected final StripePriceUpdatedHandler priceUpdatedHandler;

	protected final UserRepository userRepository;
	protected final TenantService tenantService;
	protected final StripeEventLogRepository stripeEventLogRepository;

	@Value("${stripe.session.return-url}")
	private String stripeSessionReturnUrl;

	@PostConstruct
	public void init() {
		Stripe.apiKey = stripeProperties.getSecretKey();
	}

	@Autowired
	protected StripeEventsService(
		StripeEventLogRepository repository,
		StripeEventMapper mapper,
		StripeEventLogQueryBuilder queryBuilder,
		StripeProperties stripeProperties,
		StripeCheckoutSessionCompletedHandler checkoutSessionHandler,
		StripeSubscriptionCreatedHandler subscriptionCreatedHandler,
		StripeSubscriptionUpdatedHandler subscriptionUpdatedHandler,
		StripeSubscriptionDeletedHandler subscriptionDeletedHandler,
		StripeSubscriptionPausedHandler subscriptionPausedHandler,
		StripeSubscriptionResumedHandler subscriptionResumedHandler,
		StripeInvoicePaymentFailedHandler invoicePaymentFailedHandler,
		StripeInvoicePaymentSucceededHandler invoicePaymentSucceededHandler,
		StripeInvoicePaidHandler invoicePaidHandler,
		StripeInvoiceFinalizedHandler invoiceFinalizedHandler,
		StripeInvoiceCreatedHandler invoiceCreatedHandler,
		StripeSetupIntentCreatedHandler setupIntentCreatedHandler,
		StripeSetupIntentSucceededHandler setupIntentSucceededHandler,
		StripeCustomerCreatedHandler customerCreatedHandler,
		StripeCustomerUpdatedHandler customerUpdatedHandler,
		StripeCustomerDeletedHandler customerDeletedHandler,
		StripePaymentMethodAttachedHandler paymentMethodAttachedHandler,
		StripeProductCreatedHandler productCreatedHandler,
		StripeProductUpdatedHandler productUpdatedHandler,
		StripePriceCreatedHandler priceCreatedHandler,
		StripePriceUpdatedHandler priceUpdatedHandler,
		UserRepository userRepository,
		TenantService tenantService
	) {
		super(repository, mapper, queryBuilder);
		this.stripeProperties = stripeProperties;
		this.stripeEventLogRepository = repository;
		this.checkoutSessionHandler = checkoutSessionHandler;
		this.subscriptionCreatedHandler = subscriptionCreatedHandler;
		this.subscriptionUpdatedHandler = subscriptionUpdatedHandler;
		this.subscriptionDeletedHandler = subscriptionDeletedHandler;
		this.subscriptionPausedHandler = subscriptionPausedHandler;
		this.subscriptionResumedHandler = subscriptionResumedHandler;
		this.invoicePaymentFailedHandler = invoicePaymentFailedHandler;
		this.invoicePaymentSucceededHandler = invoicePaymentSucceededHandler;
		this.invoicePaidHandler = invoicePaidHandler;
		this.invoiceFinalizedHandler = invoiceFinalizedHandler;
		this.invoiceCreatedHandler = invoiceCreatedHandler;
		this.setupIntentCreatedHandler = setupIntentCreatedHandler;
		this.setupIntentSucceededHandler = setupIntentSucceededHandler;
		this.customerCreatedHandler = customerCreatedHandler;
		this.customerUpdatedHandler = customerUpdatedHandler;
		this.customerDeletedHandler = customerDeletedHandler;
		this.paymentMethodAttachedHandler = paymentMethodAttachedHandler;
		this.productCreatedHandler = productCreatedHandler;
		this.productUpdatedHandler = productUpdatedHandler;
		this.priceCreatedHandler = priceCreatedHandler;
		this.priceUpdatedHandler = priceUpdatedHandler;
		this.userRepository = userRepository;
		this.tenantService = tenantService;
	}

	public String handleEvent(Event event) throws QorvaException {
		// Step 9: idempotency — ignore duplicate events
		if (stripeEventLogRepository.existsByStripeEventId(event.getId())) {
			log.info("Duplicate Stripe event id={} type={}, skipping", event.getId(), event.getType());
			return "duplicate";
		}

		try {
			Optional<StripeObject> opt = resolveStripeObject(event);

			switch (event.getType()) {
				case CUSTOMER_SUBSCRIPTION_CREATED -> subscriptionCreatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case CUSTOMER_SUBSCRIPTION_UPDATED -> subscriptionUpdatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case CUSTOMER_SUBSCRIPTION_DELETED -> subscriptionDeletedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case CUSTOMER_SUBSCRIPTION_PAUSED -> subscriptionPausedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case CUSTOMER_SUBSCRIPTION_RESUMED -> subscriptionResumedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case CHECKOUT_SESSION_COMPLETED -> checkoutSessionHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case INVOICE_PAYMENT_FAILED -> invoicePaymentFailedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case INVOICE_PAYMENT_SUCCEEDED -> invoicePaymentSucceededHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case INVOICE_PAID -> invoicePaidHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case INVOICE_FINALIZED -> invoiceFinalizedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case INVOICE_CREATED -> invoiceCreatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case SETUP_INTENT_CREATED -> setupIntentCreatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case SETUP_INTENT_SUCCEEDED -> setupIntentSucceededHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case CUSTOMER_CREATED -> customerCreatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case CUSTOMER_UPDATED -> customerUpdatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case CUSTOMER_DELETED -> customerDeletedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case PAYMENT_METHOD_ATTACHED -> paymentMethodAttachedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case PRODUCT_CREATED -> productCreatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case PRODUCT_UPDATED -> productUpdatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case PRICE_CREATED -> priceCreatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				case PRICE_UPDATED -> priceUpdatedHandler.handle(opt.orElseThrow(() -> missingObject(event)), event.getId());
				default -> log.debug("Ignoring unhandled Stripe event type={}", event.getType());
			}
		} catch (QorvaException e) {
			log.error("Failed to handle event: {}", event.getId(), e);
			throw e;
		} catch (Exception e) {
			log.error("Unhandled exception while handling event: {}", event.getId(), e);
			throw new QorvaException("Unhandled exception while handling event: " + event.getId(), e);
		}
		return "success";
	}

	// Step 10: return current subscription status for the authenticated user
	public SubscriptionStatusResponseDTO getSubscriptionStatus(@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		var user = Optional.ofNullable(userRepository.findByEmail(userDetails.getUsername()))
			.orElseThrow(() -> new QorvaException("User not found"));
		var tenant = tenantService.findOneById(user.getTenantId());
		var info = tenant.getSubscriptionInfo();
		return new SubscriptionStatusResponseDTO(
			info.getSubscriptionStatus(),
			info.getSubscriptionPlan(),
			info.getCurrentPeriodEnd(),
			info.getCancelAtPeriodEnd()
		);
	}

	private Optional<StripeObject> resolveStripeObject(Event event) {
		EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
		Optional<? extends StripeObject> opt = deserializer.getObject();
		if (opt.isPresent()) {
			return Optional.of(opt.get());
		}
		// Stripe SDK version mismatch — fall back to raw JSON deserialization
		Class<? extends StripeObject> targetClass = stripeClassForEvent(event.getType());
		if (targetClass == null) {
			return Optional.empty();
		}
		try {
			StripeObject obj = ApiResource.GSON.fromJson(deserializer.getRawJson(), targetClass);
			return Optional.ofNullable(obj);
		} catch (Exception e) {
			log.warn("Raw JSON fallback deserialization failed for event type={}", event.getType(), e);
			return Optional.empty();
		}
	}

	private Class<? extends StripeObject> stripeClassForEvent(String eventType) {
		return switch (eventType) {
			case CUSTOMER_SUBSCRIPTION_CREATED,
				 CUSTOMER_SUBSCRIPTION_UPDATED,
				 CUSTOMER_SUBSCRIPTION_DELETED,
				 CUSTOMER_SUBSCRIPTION_PAUSED,
				 CUSTOMER_SUBSCRIPTION_RESUMED -> Subscription.class;
			case CHECKOUT_SESSION_COMPLETED -> com.stripe.model.checkout.Session.class;
			case INVOICE_PAYMENT_FAILED,
				 INVOICE_PAYMENT_SUCCEEDED,
				 INVOICE_PAID,
				 INVOICE_FINALIZED,
				 INVOICE_CREATED -> Invoice.class;
			case SETUP_INTENT_CREATED,
				 SETUP_INTENT_SUCCEEDED -> SetupIntent.class;
			case CUSTOMER_CREATED,
				 CUSTOMER_UPDATED,
				 CUSTOMER_DELETED -> Customer.class;
			case PAYMENT_METHOD_ATTACHED -> PaymentMethod.class;
			case PRODUCT_CREATED, PRODUCT_UPDATED -> Product.class;
			case PRICE_CREATED, PRICE_UPDATED -> Price.class;
			default -> null;
		};
	}

	private QorvaException missingObject(Event event) {
		log.error("Could not deserialize data.object for handled event type={} id={}", event.getType(), event.getId());
		return new QorvaException("Could not deserialize Stripe event object for type=" + event.getType());
	}

	public PortalSession buildStripePortalSessionUrl(@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		var user = Optional.ofNullable(userRepository.findByEmail(userDetails.getUsername()))
			.orElseThrow(() -> new QorvaException("User not found"));
		var tenant = tenantService.findOneById(user.getTenantId());

		SessionCreateParams params = SessionCreateParams.builder()
			.setCustomer(tenant.getStripeCustomerId())
			.setReturnUrl(stripeSessionReturnUrl)
			.build();

		try {
			return new PortalSession(Session.create(params).getUrl());
		} catch (StripeException e) {
			log.error("Failed to create Stripe portal session", e);
			throw new QorvaException(QorvaErrorCodes.BILLING_PORTAL_FAILED, e);
		}
	}
}
