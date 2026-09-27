package ai.qorva.core.service.handlers;

import ai.qorva.core.exception.QorvaException;
import com.stripe.model.StripeObject;

import java.util.Set;

/** Handles one or more Stripe event types; registered with the StripeEventDispatcher by type. */
public interface StripeEventHandler {

	/** The Stripe event types this handler processes (e.g. {@code invoice.paid}). */
	Set<String> eventTypes();

	/** The object those events carry, for when the SDK version cannot deserialize it itself. */
	Class<? extends StripeObject> objectType();

	void handle(StripeObject obj) throws QorvaException;

	default void handle(StripeObject obj, String eventId) throws QorvaException {
		handle(obj);
	}
}
