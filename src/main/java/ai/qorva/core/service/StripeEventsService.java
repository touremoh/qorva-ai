package ai.qorva.core.service;

import ai.qorva.core.dao.entity.StripeEventLog;
import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.qbe.StripeEventLogQueryBuilder;
import com.stripe.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripeEventsService extends AbstractQorvaService<StripeEventLogDTO, StripeEventLog> {

	protected static final String CUSTOMER_SUBSCRIPTION_CREATED = "customer.subscription.created";
	protected static final String CUSTOMER_SUBSCRIPTION_UPDATED = "customer.subscription.updated";
	protected static final String CUSTOMER_SUBSCRIPTION_PAUSED = "customer.subscription.paused";
	protected static final String CUSTOMER_SUBSCRIPTION_DELETED = "customer.subscription.deleted";


	protected final UserService userService;
	protected final TenantService tenantService;

	@Autowired
	protected StripeEventsService(StripeEventLogRepository repository, StripeEventMapper mapper, UserService userService, StripeEventLogQueryBuilder queryBuilder, TenantService tenantService) {
		super(repository, mapper, queryBuilder);
		this.userService = userService;
		this.tenantService = tenantService;
	}

	public void handleEvent(Event event) throws QorvaException {
		try {
			// See events types here: https://docs.stripe.com/api/events/types
			switch (event.getType()) {
				case CUSTOMER_SUBSCRIPTION_CREATED -> handleSubscriptionCreated(event);
				case CUSTOMER_SUBSCRIPTION_UPDATED -> handleSubscriptionUpdated(event);
				case CUSTOMER_SUBSCRIPTION_PAUSED -> handleSubscriptionPaused(event);
				case CUSTOMER_SUBSCRIPTION_DELETED -> handleSubscriptionDeleted(event);
				default -> unhandledEvent(event);
			}
		} catch (QorvaException e) {
			log.error("Failed to handle event: {}", event.getId(), e);
			throw e;
		} catch (Exception e) {
			log.error("Unhandled exception while handling event: {}", event.getId(), e);
			throw new QorvaException("Unhandled exception while handling event: " + event.getId(), e);
		}
	}

	private void handleSubscriptionCreated(Event event) throws QorvaException {
		log.debug("Handling subscription creation event: {}", event.getData());

		// Map Stripe to Qorva Event
		var eventData = ((StripeEventMapper)this.mapper).mapStripeEventToDTO(event);

		// Find the tenantId of the event
		var tenant = this.tenantService.findOneById(eventData.getTenantId());

		// TODO Update the subscription plan

		// Persist the event
		this.createOne(eventData);
	}

	private void handleSubscriptionDeleted(Event event) {
		log.debug("Subscription deleted: {}", event.getData());
	}

	private void handleSubscriptionPaused(Event event) {
		log.debug("Subscription paused: {}", event.getData());
	}

	private void handleSubscriptionUpdated(Event event) {
		log.debug("Subscription updated: {}", event.getData());
	}
	private void unhandledEvent(Event event) {
		log.warn("Unhandled event: {}", event.getType());
	}
}
