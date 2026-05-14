package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.utils.SubscriptionStatusHelper;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class StripeSubscriptionCreatedHandler implements StripeEventHandler {

	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;
	private final UserRepository userRepository;
	private final TenantService tenantService;

	@Autowired
	public StripeSubscriptionCreatedHandler(StripeEventLogRepository repository, StripeEventMapper evtMapper, UserRepository userRepository, TenantService tenantService) {
		this.repository = repository;
		this.evtMapper = evtMapper;
		this.userRepository = userRepository;
		this.tenantService = tenantService;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		log.info("Handling customer.subscription.created event");

		Subscription sub = (Subscription) obj;
		var stripeCustomerId = sub.getCustomer();
		var subscriptionId = sub.getId();
		var subscriptionStatus = SubscriptionStatusHelper.subscriptionFromCode(sub.getStatus());

		String tenantId = null;

		try {
			var stripeCustomerDetails = Customer.retrieve(stripeCustomerId);
			var customerEmail = stripeCustomerDetails.getEmail();
			var user = Optional.ofNullable(this.userRepository.findByEmail(customerEmail))
				.orElseThrow(() -> new QorvaException("User not found for email " + customerEmail));
			tenantId = user.getTenantId();
		} catch (StripeException ex) {
			log.warn("Failed to retrieve Stripe customer {} to resolve tenantId", stripeCustomerId);
			throw new QorvaException("Failed to retrieve customer details for customer " + stripeCustomerId, ex);
		}

		// Update tenant subscriptionInfo — idempotent; checkout.session.completed may have already set full details
		try {
			var tenant = tenantService.findOneById(tenantId);
			var info = tenant.getSubscriptionInfo() != null ? tenant.getSubscriptionInfo() : new SubscriptionInfo();
			info.setSubscriptionId(subscriptionId);
			info.setSubscriptionStatus(subscriptionStatus);
			if (info.getSubscriptionStartDate() == null && sub.getStartDate() != null) {
				info.setSubscriptionStartDate(Instant.ofEpochSecond(sub.getStartDate()));
			}
			tenant.setSubscriptionInfo(info);
			if (tenant.getStripeCustomerId() == null) {
				tenant.setStripeCustomerId(stripeCustomerId);
			}
			tenantService.updateOne(tenantId, tenant);
		} catch (QorvaException e) {
			log.warn("Could not update subscriptionInfo on tenant {} for subscription.created: {}", tenantId, e.getMessage());
		}

		// Log event
		var eventLog = new StripeEventLogDTO();
		eventLog.setEventType("customer.subscription.created");
		eventLog.setEventStatus(subscriptionStatus);
		eventLog.setStripeCustomerId(stripeCustomerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		eventLog.setTenantId(tenantId);
		this.repository.save(this.evtMapper.map(eventLog));

		log.debug("Subscription created for customer={} subscriptionId={}", stripeCustomerId, subscriptionId);
	}
}
