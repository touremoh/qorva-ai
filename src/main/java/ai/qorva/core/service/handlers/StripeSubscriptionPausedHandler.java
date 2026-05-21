package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripeSubscriptionPausedHandler implements StripeEventHandler {

	private final TenantService tenantService;
	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;

	@Autowired
	public StripeSubscriptionPausedHandler(TenantService tenantService, StripeEventLogRepository repository, StripeEventMapper evtMapper) {
		this.tenantService = tenantService;
		this.repository = repository;
		this.evtMapper = evtMapper;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		log.info("Handling customer.subscription.paused event");

		Subscription sub = (Subscription) obj;
		var stripeCustomerId = sub.getCustomer();
		var subscriptionId = sub.getId();

		TenantDTO tenant;
		try {
			tenant = tenantService.findOneByCriteria(TenantDTO.builder().stripeCustomerId(stripeCustomerId).build());
		} catch (QorvaException e) {
			log.warn("Failed to retrieve tenant for customer {}", stripeCustomerId);
			throw new QorvaException("Failed to retrieve tenant for customer " + stripeCustomerId, e);
		}

		tenant.getSubscriptionInfo().setSubscriptionStatus(SubscriptionStatus.PAUSED.getValue());
		tenantService.updateOne(tenant.getTenantId(), tenant);

		var eventLog = new StripeEventLogDTO();
		eventLog.setEventType("customer.subscription.paused");
		eventLog.setEventStatus(SubscriptionStatus.PAUSED.getValue());
		eventLog.setStripeCustomerId(stripeCustomerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		eventLog.setTenantId(tenant.getTenantId());
		repository.save(evtMapper.map(eventLog));

		log.info("Subscription paused for customer {}. Status set to LOCKED.", stripeCustomerId);
	}
}
