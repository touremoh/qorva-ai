package ai.qorva.core.service;

import ai.qorva.core.dao.entity.StripeWebhookEvent;
import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.StripeWebhookEventRepository;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.handlers.StripeEventHandler;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.net.ApiResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Routes a verified Stripe event to the handler registered for its type, exactly once per event id.
 * The id is claimed in the stripe_webhook_events ledger before the handler runs (an insert, so a
 * concurrent re-delivery loses the race) and released again if the handler fails, so Stripe's retry
 * can process it.
 */
@Slf4j
@Service
public class StripeEventDispatcher {

	public static final String DUPLICATE = "duplicate";
	public static final String SUCCESS = "success";

	private final Map<String, StripeEventHandler> handlers = new HashMap<>();
	private final StripeWebhookEventRepository ledger;
	private final StripeEventLogRepository eventLogRepository;

	public StripeEventDispatcher(List<StripeEventHandler> handlers, StripeWebhookEventRepository ledger,
	                             StripeEventLogRepository eventLogRepository) {
		for (var handler : handlers) {
			for (var type : handler.eventTypes()) {
				var previous = this.handlers.put(type, handler);
				if (previous != null) {
					throw new IllegalStateException("Two Stripe handlers for " + type + ": "
						+ previous.getClass().getSimpleName() + " and " + handler.getClass().getSimpleName());
				}
			}
		}
		this.ledger = ledger;
		this.eventLogRepository = eventLogRepository;
	}

	public String dispatch(Event event) throws QorvaException {
		if (alreadyReceived(event.getId())) {
			log.info("Duplicate Stripe event id={} type={}, skipping", event.getId(), event.getType());
			return DUPLICATE;
		}
		var handler = handlers.get(event.getType());
		if (handler == null) {
			log.debug("Ignoring unhandled Stripe event type={}", event.getType());
			return SUCCESS;
		}
		if (!claim(event)) {
			log.info("Stripe event id={} is being handled by another delivery, skipping", event.getId());
			return DUPLICATE;
		}
		try {
			var object = resolveObject(event, handler.objectType())
				.orElseThrow(() -> missingObject(event));
			handler.handle(object, event.getId());
			markProcessed(event);
			return SUCCESS;
		} catch (QorvaException e) {
			release(event);
			log.error("Failed to handle event: {}", event.getId(), e);
			throw e;
		} catch (Exception e) {
			release(event);
			log.error("Unhandled exception while handling event: {}", event.getId(), e);
			throw new QorvaException("Unhandled exception while handling event: " + event.getId(), e);
		}
	}

	/** Also honours event ids recorded on stripe_event_logs before the ledger existed. */
	private boolean alreadyReceived(String eventId) {
		return ledger.existsById(eventId) || eventLogRepository.existsByStripeEventId(eventId);
	}

	private boolean claim(Event event) {
		try {
			ledger.insert(new StripeWebhookEvent(event.getId(), event.getType(), StripeWebhookEvent.STATUS_PROCESSING, Instant.now(), null));
			return true;
		} catch (DuplicateKeyException alreadyClaimed) {
			return false;
		}
	}

	private void markProcessed(Event event) {
		ledger.save(new StripeWebhookEvent(event.getId(), event.getType(), StripeWebhookEvent.STATUS_PROCESSED, Instant.now(), Instant.now()));
	}

	private void release(Event event) {
		try {
			ledger.deleteById(event.getId());
		} catch (Exception e) {
			log.warn("Could not release Stripe event {} after a failure: {}", event.getId(), e.getMessage());
		}
	}

	private static Optional<StripeObject> resolveObject(Event event, Class<? extends StripeObject> objectType) {
		var deserializer = event.getDataObjectDeserializer();
		Optional<? extends StripeObject> object = deserializer.getObject();
		if (object.isPresent()) {
			return Optional.of(object.get());
		}
		// Stripe SDK version mismatch — fall back to raw JSON deserialization into the handler's type.
		try {
			return Optional.ofNullable(ApiResource.GSON.fromJson(deserializer.getRawJson(), objectType));
		} catch (Exception e) {
			log.warn("Raw JSON fallback deserialization failed for event type={}", event.getType(), e);
			return Optional.empty();
		}
	}

	private static QorvaException missingObject(Event event) {
		log.error("Could not deserialize data.object for handled event type={} id={}", event.getType(), event.getId());
		return new QorvaException("Could not deserialize Stripe event object for type=" + event.getType());
	}
}
