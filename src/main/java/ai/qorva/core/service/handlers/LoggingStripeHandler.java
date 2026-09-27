package ai.qorva.core.service.handlers;

import ai.qorva.core.exception.QorvaException;
import com.stripe.model.StripeObject;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.function.Function;

/**
 * A Stripe event Qorva only records: which object it carries and what goes into the log row. The
 * instances are declared in {@link LoggingStripeHandlers}, one per event type.
 */
@Slf4j
public class LoggingStripeHandler<T extends StripeObject> implements StripeEventHandler {

	private final String eventType;
	private final Class<T> objectType;
	private final Function<T, StripeEventLogWriter.Entry> entry;
	private final StripeEventLogWriter writer;

	public LoggingStripeHandler(String eventType, Class<T> objectType,
	                            Function<T, StripeEventLogWriter.Entry> entry, StripeEventLogWriter writer) {
		this.eventType = eventType;
		this.objectType = objectType;
		this.entry = entry;
		this.writer = writer;
	}

	@Override
	public Set<String> eventTypes() {
		return Set.of(eventType);
	}

	@Override
	public Class<? extends StripeObject> objectType() {
		return objectType;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		log.info("Handling {} event", eventType);
		writer.write(eventType, entry.apply(objectType.cast(obj)));
	}
}
