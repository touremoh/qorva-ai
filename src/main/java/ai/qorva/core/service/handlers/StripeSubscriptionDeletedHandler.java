package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.service.UserService;
import ai.qorva.core.utils.SubscriptionStatusHelper;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class StripeSubscriptionDeletedHandler implements StripeEventHandler {

	private final TenantService tenantService;
	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;
	private final UserService userService;

	@Autowired
	public StripeSubscriptionDeletedHandler(
		TenantService tenantService,
		StripeEventLogRepository repository,
		StripeEventMapper evtMapper,
		UserService userService
	) {
		this.tenantService = tenantService;
		this.repository = repository;
		this.evtMapper = evtMapper;
		this.userService = userService;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		log.info("Handling subscription deletion event");

		// Parse the event
		Subscription sub = (Subscription) obj;

		// Extract the relevant fields from the event
		var stripeCustomerId = sub.getCustomer();
		var subscriptionId = sub.getId();
		var subscriptionStatus = sub.getStatus();
		var canceledAt = sub.getCanceledAt();

		TenantDTO tenant = null;
		try {
			tenant = this.tenantService.findOneByCriteria(TenantDTO.builder().stripeCustomerId(stripeCustomerId).build());
		} catch (QorvaException e) {
			log.warn("Tenant not found for Stripe customer {} during subscription.deleted — event will be logged without tenantId", stripeCustomerId);
		}

		// Persist the event log (with or without tenantId)
		var eventLog = new StripeEventLogDTO();
		eventLog.setEventType("customer.subscription.deleted");
		eventLog.setEventStatus(subscriptionStatus);
		eventLog.setStripeCustomerId(stripeCustomerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		if (tenant != null) {
			eventLog.setTenantId(tenant.getTenantId());
		}
		this.repository.save(this.evtMapper.map(eventLog));

		if (tenant == null) {
			log.info("Subscription deleted for unknown customer {} — no tenant to update.", stripeCustomerId);
			return;
		}

		// Update the tenant's subscription info (cancellation status and canceled at)
		tenant.getSubscriptionInfo().setSubscriptionStatus(SubscriptionStatusHelper.subscriptionFromCode(subscriptionStatus));
		tenant.getSubscriptionInfo().setSubscriptionEndDate(canceledAt == null ? null : Instant.ofEpochSecond(canceledAt));

		this.tenantService.updateOne(tenant.getTenantId(), tenant);

		// Change the status of all users associated with the tenant
		var nbUsersLocked = this.userService.updateUserAccountStatusByTenantId(tenant.getTenantId(), UserStatusEnum.DELETED.getValue());

		// Log the event
		log.info("Subscription deleted for customer {}. {} users were locked.", stripeCustomerId, nbUsersLocked);
	}
}
