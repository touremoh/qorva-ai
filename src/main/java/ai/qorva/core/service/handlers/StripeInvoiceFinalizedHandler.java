package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.UserRepository;
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
public class StripeInvoiceFinalizedHandler extends AbstractStripeLoggingHandler {

	@Autowired
	public StripeInvoiceFinalizedHandler(StripeEventLogRepository repository, StripeEventMapper evtMapper, TenantService tenantService, UserRepository userRepository) {
		super(repository, evtMapper, tenantService, userRepository);
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		log.info("Handling invoice.finalized event");
		Invoice invoice = (Invoice) obj;
		String subscriptionId = invoice.getParent() != null && invoice.getParent().getSubscriptionDetails() != null
			? invoice.getParent().getSubscriptionDetails().getSubscription()
			: null;
		saveEventLog("invoice.finalized", invoice.getCustomer(), subscriptionId, invoice.getStatus());
	}
}
