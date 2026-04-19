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
public class StripeInvoicePaymentFailedHandler implements StripeEventHandler {

	private static final String EVENT_TYPE = "invoice.payment_failed";

	private final TenantService tenantService;
	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;

	@Autowired
	public StripeInvoicePaymentFailedHandler(
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
		log.info("Handling invoice.payment_failed event");

		Invoice invoice = (Invoice) obj;
		var stripeCustomerId = invoice.getCustomer();
		var subscriptionId = invoice.getParent() != null && invoice.getParent().getSubscriptionDetails() != null
			? invoice.getParent().getSubscriptionDetails().getSubscription()
			: null;

		TenantDTO tenant;
		try {
			tenant = tenantService.findOneByData(TenantDTO.builder().stripeCustomerId(stripeCustomerId).build());
		} catch (QorvaException e) {
			log.warn("Failed to retrieve tenant for customer {}", stripeCustomerId);
			throw new QorvaException("Failed to retrieve tenant for customer " + stripeCustomerId, e);
		}

		// Log the failed payment event
		var eventLog = new StripeEventLogDTO();
		eventLog.setEventType(EVENT_TYPE);
		eventLog.setEventStatus("payment_failed");
		eventLog.setStripeCustomerId(stripeCustomerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		eventLog.setTenantId(tenant.getId());
		repository.save(evtMapper.map(eventLog));

		// Update the tenant's subscription status to payment failed
		tenant.getSubscriptionInfo().setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_PAYMENT_FAILED.getValue());
		tenantService.updateOne(tenant.getId(), tenant);

		log.info("Payment failed for customer {}. Subscription status set to PAYMENT_FAILED.", stripeCustomerId);
	}
}
