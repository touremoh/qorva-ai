package ai.qorva.core.service;

import ai.qorva.core.config.StripeProperties;
import ai.qorva.core.dao.entity.StripeEventLog;
import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.*;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.qbe.StripeEventLogQueryBuilder;
import ai.qorva.core.service.handlers.*;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.billingportal.Session;
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

	protected final StripeProperties stripeProperties;

	protected final StripeCheckoutSessionCompletedHandler checkoutSessionHandler;
	protected final StripeSubscriptionCreatedHandler subscriptionCreatedHandler;
	protected final StripeSubscriptionUpdatedHandler subscriptionUpdatedHandler;
	protected final StripeSubscriptionDeletedHandler subscriptionDeletedHandler;
	protected final StripeSubscriptionPausedHandler subscriptionPausedHandler;
	protected final StripeSubscriptionResumedHandler subscriptionResumedHandler;

	protected final UserRepository userRepository;
	protected final TenantService tenantService;

	@Value( "${stripe.session.return-url}")
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
		UserRepository userRepository, TenantService tenantService
	) {

		super(repository, mapper, queryBuilder);
		this.stripeProperties = stripeProperties;
		this.checkoutSessionHandler = checkoutSessionHandler;
		this.subscriptionCreatedHandler = subscriptionCreatedHandler;
		this.subscriptionUpdatedHandler = subscriptionUpdatedHandler;
		this.subscriptionDeletedHandler = subscriptionDeletedHandler;
		this.subscriptionPausedHandler = subscriptionPausedHandler;
		this.subscriptionResumedHandler = subscriptionResumedHandler;
		this.userRepository = userRepository;
		this.tenantService = tenantService;
	}

	public String handleEvent(Event event) throws QorvaException {
		try {
			EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
			Optional<? extends StripeObject> opt = deserializer.getObject();

			if (opt.isEmpty()) {
				log.warn("Unable to deserialize data.object for type={}", event.getType());
				return "ignore";
			}
			switch (event.getType()) {
				case CUSTOMER_SUBSCRIPTION_CREATED -> this.subscriptionCreatedHandler.handle(opt.get());
				case CUSTOMER_SUBSCRIPTION_UPDATED -> this.subscriptionUpdatedHandler.handle(opt.get());
				case CUSTOMER_SUBSCRIPTION_DELETED -> this.subscriptionDeletedHandler.handle(opt.get());
				case CUSTOMER_SUBSCRIPTION_PAUSED -> this.subscriptionPausedHandler.handle(opt.get());
				case CUSTOMER_SUBSCRIPTION_RESUMED -> this.subscriptionResumedHandler.handle(opt.get());
				case CHECKOUT_SESSION_COMPLETED -> this.checkoutSessionHandler.handle(event);
				default -> unhandledEvent(event);
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

	private void unhandledEvent(Event event) {
		log.warn("Unhandled event: {}", event.getType());
	}


	public PortalSession buildStripePortalSessionUrl(@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		// Get the logged user
		var user = Optional.ofNullable(this.userRepository.findByEmail(userDetails.getUsername()))
			               .orElseThrow(() -> new QorvaException("User not found"));

		// Get the tenant id
		var tenant = this.tenantService.findOneById(user.getTenantId());

		// Build the url
		SessionCreateParams params = SessionCreateParams.builder()
			.setCustomer(tenant.getStripeCustomerId())
			.setReturnUrl(this.stripeSessionReturnUrl)
			.build();

		try {
			return new PortalSession(Session.create(params).getUrl());
		} catch (StripeException e) {
			log.error("Failed to create Stripe portal session", e);
			throw new QorvaException("Failed to create Stripe portal session", e);
		}
	}
}
