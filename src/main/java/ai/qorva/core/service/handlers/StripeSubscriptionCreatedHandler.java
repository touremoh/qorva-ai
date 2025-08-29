package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class StripeSubscriptionCreatedHandler implements StripeEventHandler {

	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;
	private final UserRepository userRepository;

	@Autowired
	public StripeSubscriptionCreatedHandler(StripeEventLogRepository repository, StripeEventMapper evtMapper, UserRepository userRepository) {
		this.repository = repository;
		this.evtMapper = evtMapper;
		this.userRepository = userRepository;
	}

	@Override
	public void handle(Event event) throws QorvaException {
		log.debug("Handling subscription created: {}", event.getData());

		// Get the event payload
		var parsedEvent = this.evtMapper.mapStripeEventToEventSubscriptionCreated(event);

		var stripeEventLogDto = new StripeEventLogDTO();
		stripeEventLogDto.setEventType(event.getType());
		stripeEventLogDto.setEventStatus(parsedEvent.getStatus());
		stripeEventLogDto.setStripeCustomerId(parsedEvent.getCustomer());

		if (Objects.nonNull(parsedEvent.getItems()) && Objects.nonNull(parsedEvent.getItems().getData())) {
			stripeEventLogDto.setStripeSubscriptionId(parsedEvent.getItems().getData().getFirst().getSubscription());
		}

		// From the customer ID find the corresponding email.
		try {
			var stripeCustomerDetails = Customer.retrieve(parsedEvent.getCustomer());
			var customerEmail = stripeCustomerDetails.getEmail();
			var userDetails = Optional.ofNullable(this.userRepository.findByEmail(customerEmail)).orElseThrow(() -> new QorvaException("User not found"));
			stripeEventLogDto.setTenantId(userDetails.getTenantId());
		} catch (StripeException ex) {
			log.warn("Failed to retrieve customer details for customer {}", parsedEvent.getCustomer());
			throw new QorvaException("Failed to retrieve customer details for customer " + parsedEvent.getCustomer(), ex);
		} catch (QorvaException ex) {
			log.warn("User not found for email {}", parsedEvent.getCustomer());
			throw new QorvaException("User not found for email " + parsedEvent.getCustomer());
		}

		// Save the event to the database
		var savedEvent = this.repository.save(this.evtMapper.map(stripeEventLogDto));

		log.debug("Saved event to database: {}", savedEvent);
	}
}
