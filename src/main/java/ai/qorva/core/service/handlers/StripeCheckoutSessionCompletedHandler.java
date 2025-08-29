package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.utils.SubscriptionStatusHelper;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@Service
public class StripeCheckoutSessionCompletedHandler implements StripeEventHandler {

	private final TenantService tenantService;
	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;
	private final UserRepository userRepository;

	@Autowired
	public StripeCheckoutSessionCompletedHandler(TenantService tenantService, StripeEventLogRepository repository, StripeEventMapper evtMapper, UserRepository userRepository) {
		this.tenantService = tenantService;
		this.repository = repository;
		this.evtMapper = evtMapper;
		this.userRepository = userRepository;
	}

	@Override
	public void handle(Event event) throws QorvaException {
		log.debug("Handling checkout session completed: {}", event.getData());

		// Get the event payload
		var parsedEvent = this.evtMapper.mapStripeEventToEventCheckoutSessionCompleted(event);

		var customerId = parsedEvent.getCustomer();
		var subscriptionId = parsedEvent.getSubscription();
		var tenantId = parsedEvent.getClientReferenceId();
		var customerEmail = parsedEvent.getCustomerEmail();

		// Find the user by email
		var user = this.userRepository.findByEmail(customerEmail);

		// Check if the user was found
		if (Objects.isNull(user)) {
			log.warn("User not found for email {}", customerEmail);
			throw new QorvaException("User not found for email " + customerEmail);
		}

		// Get subscription details
		Subscription subscriptionDetails;
		try {
			subscriptionDetails = Subscription.retrieve(subscriptionId);
		} catch (StripeException e) {
			log.error("Failed to retrieve subscription details for subscription id {}", subscriptionId);
			throw new QorvaException("Failed to retrieve subscription details for subscription id " + subscriptionId, e);
		}

		var productId = subscriptionDetails.getItems().getData().getFirst().getPlan().getProduct();
		var subscriptionAmount = new Decimal128(subscriptionDetails.getItems().getData().getFirst().getPlan().getAmount());
		var billingCycle = subscriptionDetails.getItems().getData().getFirst().getPlan().getInterval();
		var subscriptionStatus = SubscriptionStatusHelper.subscriptionFromCode(subscriptionDetails.getStatus());

		String subscriptionPlan = "";
		try {
			subscriptionPlan = Product.retrieve(productId).getName();
		} catch (StripeException e) {
			log.error("Failed to retrieve product details for product id {}", productId);
			throw new QorvaException("Failed to retrieve product details for product id " + productId, e);
		}

		var subscriptionInfo = new SubscriptionInfo();
		subscriptionInfo.setSubscriptionId(subscriptionId);
		subscriptionInfo.setSubscriptionStatus(subscriptionStatus);
		subscriptionInfo.setSubscriptionStartDate(Instant.now());
		subscriptionInfo.setSubscriptionPlan(subscriptionPlan);
		subscriptionInfo.setBillingCycle(billingCycle);
		subscriptionInfo.setPrice(subscriptionAmount);
		subscriptionInfo.setPriceId(subscriptionDetails.getItems().getData().getFirst().getPlan().getId());

		// Update the user's subscription info
		var tenant = this.tenantService.findOneById(tenantId);

		// Update the tenant's subscription info'
		tenant.setSubscriptionInfo(subscriptionInfo);
		tenant.setStripeCustomerId(customerId);

		// Save the tenant
		this.tenantService.updateOne(tenantId, tenant);

		// Update the user's subscription status'
		user.setUserAccountStatus(UserStatusEnum.ACTIVE.getValue());
		this.userRepository.save(user);

		// Persist stripe event logs
		var eventLog = new StripeEventLogDTO();
		eventLog.setEventType(event.getType());
		eventLog.setEventStatus(subscriptionStatus);
		eventLog.setStripeCustomerId(customerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		eventLog.setTenantId(tenantId);
		eventLog.setEventStatus(parsedEvent.getStatus());

		this.repository.save(this.evtMapper.map(eventLog));

		log.debug("Completed checkout session for customer {} : subscription id: {}", customerEmail, subscriptionId);
	}
}
