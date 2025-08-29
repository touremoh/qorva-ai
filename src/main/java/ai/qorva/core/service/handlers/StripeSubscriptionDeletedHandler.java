package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.service.UserService;
import ai.qorva.core.utils.SubscriptionStatusHelper;
import com.stripe.model.Event;
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
	public void handle(Event event) throws QorvaException {
		log.debug("Handling subscription deletion event: {}", event.getData());

		// Parse the event
		var parsedEvent = this.evtMapper.mapStripeEventToEventSubscriptionDeleted(event);

		// Extract the relevant fields from the event
		var eventType = event.getType();
		var stripeCustomerId = parsedEvent.getData().getObject().getCustomer();
		var subscriptionId = parsedEvent.getData().getObject().getId();
		var subscriptionStatus = parsedEvent.getData().getObject().getStatus();
		var canceledAt = parsedEvent.getData().getObject().getCanceledAt();

		TenantDTO tenant;
		try {
			tenant = this.tenantService.findOneByData(TenantDTO.builder().stripeCustomerId(stripeCustomerId).build());
		} catch (QorvaException e) {
			log.warn("Failed to retrieve tenant details for customer {}", stripeCustomerId);
			throw new QorvaException("Failed to retrieve tenant details for customer " + stripeCustomerId, e);
		}

		// Persist the event log
		var eventLog = new StripeEventLogDTO();
		eventLog.setEventType(eventType);
		eventLog.setEventStatus(subscriptionStatus);
		eventLog.setStripeCustomerId(stripeCustomerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		eventLog.setTenantId(tenant.getTenantId());

		this.repository.save(this.evtMapper.map(eventLog));

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
