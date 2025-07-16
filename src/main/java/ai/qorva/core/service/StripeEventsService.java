package ai.qorva.core.service;

import ai.qorva.core.config.StripeProperties;
import ai.qorva.core.dao.entity.StripeEventLog;
import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dto.*;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.qbe.StripeEventLogQueryBuilder;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class StripeEventsService extends AbstractQorvaService<StripeEventLogDTO, StripeEventLog> {

	protected static final String CUSTOMER_SUBSCRIPTION_CREATED = "customer.subscription.created";
	protected static final String CUSTOMER_SUBSCRIPTION_UPDATED = "customer.subscription.updated";
	protected static final String CUSTOMER_SUBSCRIPTION_PAUSED = "customer.subscription.paused";
	protected static final String CUSTOMER_SUBSCRIPTION_DELETED = "customer.subscription.deleted";


	protected final UserService userService;
	protected final TenantService tenantService;
	protected final StripeProperties stripeProperties;

	@PostConstruct
	public void init() {
		Stripe.apiKey = stripeProperties.getSecretKey();
	}

	@Autowired
	protected StripeEventsService(StripeEventLogRepository repository, StripeEventMapper mapper, UserService userService, StripeEventLogQueryBuilder queryBuilder, TenantService tenantService, StripeProperties stripeProperties) {
		super(repository, mapper, queryBuilder);
		this.userService = userService;
		this.tenantService = tenantService;
		this.stripeProperties = stripeProperties;
	}

	public StripeCustomerDTO createCustomer(StripeCustomerDTO customerDTO) throws QorvaException {
		Map<String, Object> params = new HashMap<>();
		params.put("email", customerDTO.getEmail());
		params.put("name", customerDTO.getName());
		params.put("metadata", Map.of("tenantId", customerDTO.getTenantId()));

		try {
			customerDTO.setCustomerId(Customer.create(params).getId());
		} catch (StripeException e) {
			log.error("An error occurred while trying to create a stripe customer {}", e.getMessage());
			throw new QorvaException("Stripe customer creation failed", e);
		}
		return customerDTO;
	}

	public CheckoutResponse createCheckoutSession(CheckoutRequest request) throws QorvaException {
		var tenant = Optional.ofNullable(this.tenantService.findOneById(request.tenantId()))
			                 .orElseThrow(() -> new QorvaException("Unable to start checkout session - Tenant not found"));

		Map<String, Object> params = new HashMap<>();
		params.put("customer", tenant.getStripeCustomerId());
		params.put("payment_method_types", List.of("card"));
		params.put("mode", "subscription");
		params.put("line_items", List.of(Map.of("price", request.productId(), "quantity", 1)));
		params.put("success_url", request.successUrl());
		params.put("cancel_url", request.cancelUrl());

		try {
			// Create a checkout session
			var session = Session.create(params);

			// Persist the session info in the DB
			var stripeEvent = new StripeEventLogDTO();
			stripeEvent.setEventType("USER_SUBSCRIPTION");
			stripeEvent.setEventStatus("SUBSCRIPTION_INITIATED");
			stripeEvent.setStripeCustomerId(tenant.getStripeCustomerId());
			stripeEvent.setTenantId(request.tenantId());
			this.createOne(stripeEvent);

			// Return the session id and the customer starting the payment
			return new CheckoutResponse(session.getId(), session.getCustomer());
		} catch (StripeException e) {
			log.error("An error occurred while trying to create a stripe session {}", e.getMessage());
			throw new QorvaException("Stripe session creation failed", e);
		}
	}

	public StripeEventLogDTO closeCheckout(StripeEventLogDTO event) throws QorvaException {
		return null;
	}

	public void handleEvent(Event event) throws QorvaException {
		try {
			// See events types here: https://docs.stripe.com/api/events/types
			switch (event.getType()) {
				case CUSTOMER_SUBSCRIPTION_CREATED -> handleSubscriptionCreated(event);
				case CUSTOMER_SUBSCRIPTION_UPDATED -> handleSubscriptionUpdated(event);
				case CUSTOMER_SUBSCRIPTION_PAUSED -> handleSubscriptionPaused(event);
				case CUSTOMER_SUBSCRIPTION_DELETED -> handleSubscriptionDeleted(event);
				default -> unhandledEvent(event);
			}
		} catch (QorvaException e) {
			log.error("Failed to handle event: {}", event.getId(), e);
			throw e;
		} catch (Exception e) {
			log.error("Unhandled exception while handling event: {}", event.getId(), e);
			throw new QorvaException("Unhandled exception while handling event: " + event.getId(), e);
		}
	}

	private void handleSubscriptionCreated(Event event) throws QorvaException {
		log.debug("Handling subscription creation event: {}", event.getData());

		// Map Stripe to Qorva Event
		var eventData = ((StripeEventMapper)this.mapper).mapStripeEventToDTO(event);

		// Find the tenantId of the event
		var tenant = this.tenantService.findOneById(eventData.getTenantId());

		// TODO Update the subscription plan

		// Persist the event
		this.createOne(eventData);
	}

	private void handleSubscriptionDeleted(Event event) {
		log.debug("Subscription deleted: {}", event.getData());
	}

	private void handleSubscriptionPaused(Event event) {
		log.debug("Subscription paused: {}", event.getData());
	}

	private void handleSubscriptionUpdated(Event event) {
		log.debug("Subscription updated: {}", event.getData());
	}
	private void unhandledEvent(Event event) {
		log.warn("Unhandled event: {}", event.getType());
	}
}
