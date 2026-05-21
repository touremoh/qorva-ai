package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.utils.SubscriptionStatusHelper;
import com.stripe.exception.StripeException;
import com.stripe.model.Product;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class StripeCheckoutSessionCompletedHandler implements StripeEventHandler {

	private final TenantService tenantService;
	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;
	private final UserRepository userRepository;

	@Autowired
	public StripeCheckoutSessionCompletedHandler(
		TenantService tenantService,
		StripeEventLogRepository repository,
		StripeEventMapper evtMapper,
		UserRepository userRepository
	) {
		this.tenantService = tenantService;
		this.repository = repository;
		this.evtMapper = evtMapper;
		this.userRepository = userRepository;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		handle(obj, null);
	}

	@Override
	public void handle(StripeObject obj, String eventId) throws QorvaException {
		log.info("Handling checkout.session.completed eventId={}", eventId);

		Session session = (Session) obj;

		var customerId = session.getCustomer();
		var subscriptionId = session.getSubscription();
		var tenantId = session.getClientReferenceId();
		var userId = session.getMetadata() != null ? session.getMetadata().get("userId") : null;

		// Get subscription details from Stripe
		Subscription subscriptionDetails;
		try {
			subscriptionDetails = Subscription.retrieve(subscriptionId);
		} catch (StripeException e) {
			log.error("Failed to retrieve subscription {} for tenant {}", subscriptionId, tenantId, e);
			throw new QorvaException("Failed to retrieve subscription details for subscriptionId=" + subscriptionId, e);
		}

		var subItem = subscriptionDetails.getItems().getData().getFirst();
		var productId = subItem.getPlan().getProduct();
		var subscriptionStatus = SubscriptionStatusHelper.subscriptionFromCode(subscriptionDetails.getStatus());
		var billingCycle = subItem.getPlan().getInterval();
		var subscriptionAmount = new Decimal128(subItem.getPlan().getAmount());
		var currentPeriodStart = subItem.getCurrentPeriodStart() != null
			? Instant.ofEpochSecond(subItem.getCurrentPeriodStart()) : null;
		var currentPeriodEnd = subItem.getCurrentPeriodEnd() != null
			? Instant.ofEpochSecond(subItem.getCurrentPeriodEnd()) : null;
		var cancelAtPeriodEnd = subscriptionDetails.getCancelAtPeriodEnd();

		String subscriptionPlan = "";
		try {
			subscriptionPlan = Product.retrieve(productId).getName();
		} catch (StripeException e) {
			log.error("Failed to retrieve product {} for tenant {}", productId, tenantId, e);
			throw new QorvaException("Failed to retrieve product details for productId=" + productId, e);
		}

		var subscriptionInfo = new SubscriptionInfo();
		subscriptionInfo.setSubscriptionId(subscriptionId);
		subscriptionInfo.setSubscriptionStatus(subscriptionStatus);
		subscriptionInfo.setSubscriptionStartDate(Instant.ofEpochSecond(subscriptionDetails.getStartDate()));
		subscriptionInfo.setSubscriptionPlan(subscriptionPlan);
		subscriptionInfo.setBillingCycle(billingCycle);
		subscriptionInfo.setPrice(subscriptionAmount);
		subscriptionInfo.setPriceId(subItem.getPlan().getId());
		subscriptionInfo.setPlanCode(subItem.getPlan().getId());
		subscriptionInfo.setCurrentPeriodStart(currentPeriodStart);
		subscriptionInfo.setCurrentPeriodEnd(currentPeriodEnd);
		subscriptionInfo.setCancelAtPeriodEnd(cancelAtPeriodEnd);

		// Update tenant subscription info and stripeCustomerId
		var tenant = tenantService.findOneById(tenantId);
		tenant.setSubscriptionInfo(subscriptionInfo);
		tenant.setStripeCustomerId(customerId);
		tenantService.updateOne(tenantId, tenant);

		// Activate user account
		activateUser(userId, session.getCustomerEmail());

		// Persist event log
		var eventLog = new StripeEventLogDTO();
		eventLog.setStripeEventId(eventId);
		eventLog.setEventType("checkout.session.completed");
		eventLog.setEventStatus(subscriptionStatus);
		eventLog.setStripeCustomerId(customerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		eventLog.setTenantId(tenantId);
		repository.save(evtMapper.map(eventLog));

		log.info("Checkout completed for tenant={} subscriptionId={} status={}", tenantId, subscriptionId, subscriptionStatus);
	}

	private void activateUser(String userId, String customerEmail) {
		var user = Optional.ofNullable(userId)
			.flatMap(id -> userRepository.findById(new ObjectId(id)))
			.orElseGet(() -> Objects.nonNull(customerEmail) ? userRepository.findByEmail(customerEmail) : null);

		if (user == null) {
			log.warn("Could not find user by userId={} or email={} – skipping activation", userId, customerEmail);
			return;
		}
		user.setUserAccountStatus(UserStatusEnum.ACTIVE.getValue());
		userRepository.save(user);
	}
}
