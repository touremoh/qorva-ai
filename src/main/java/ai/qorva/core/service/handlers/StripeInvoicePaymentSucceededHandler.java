package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripeInvoicePaymentSucceededHandler implements StripeEventHandler {

	private static final String EVENT_TYPE = "invoice.payment_succeeded";

	private final TenantService tenantService;
	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;

	@Autowired
	public StripeInvoicePaymentSucceededHandler(
		TenantService tenantService,
		StripeEventLogRepository repository,
		StripeEventMapper evtMapper
	) {
		this.tenantService = tenantService;
		this.repository = repository;
		this.evtMapper = evtMapper;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		log.info("Handling invoice.payment_succeeded event");

		Invoice invoice = (Invoice) obj;
		var stripeCustomerId = invoice.getCustomer();
		var subscriptionId = invoice.getParent() != null && invoice.getParent().getSubscriptionDetails() != null
			? invoice.getParent().getSubscriptionDetails().getSubscription()
			: null;

		TenantDTO tenant;
		try {
			tenant = tenantService.findOneByCriteria(TenantDTO.builder().stripeCustomerId(stripeCustomerId).build());
		} catch (QorvaException e) {
			log.warn("Failed to retrieve tenant for customer {}", stripeCustomerId);
			throw new QorvaException("Failed to retrieve tenant for customer " + stripeCustomerId, e);
		}

		// Log the successful payment event
		var eventLog = new StripeEventLogDTO();
		eventLog.setEventType(EVENT_TYPE);
		eventLog.setEventStatus("payment_succeeded");
		eventLog.setStripeCustomerId(stripeCustomerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		eventLog.setTenantId(tenant.getId());
		repository.save(evtMapper.map(eventLog));

		// Restore subscription to active (in case it was previously flagged as payment_failed)
		var info = tenant.getSubscriptionInfo();
		if (SubscriptionStatus.PAST_DUE.getValue().equals(info.getSubscriptionStatus())) {
			info.setSubscriptionStatus(SubscriptionStatus.ACTIVE.getValue());
			tenantService.updateOne(tenant.getId(), tenant);
			log.info("Payment recovered for customer {}. Subscription restored to ACTIVE.", stripeCustomerId);
		} else {
			log.debug("Payment succeeded for customer {} – subscription status unchanged ({})", stripeCustomerId, info.getSubscriptionStatus());
		}
	}
}
