package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.service.TenantService;
import com.stripe.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripeSubscriptionResumedHandler implements StripeEventHandler {

	private final TenantService tenantService;
	private final StripeEventLogRepository repository;

	@Autowired
	public StripeSubscriptionResumedHandler(TenantService tenantService, StripeEventLogRepository repository) {
		this.tenantService = tenantService;
		this.repository = repository;
	}

	@Override
	public void handle(Event event) {
		log.debug("Handling subscription created event: {}", event.getData());
	}
}
