package ai.qorva.core.service.handlers;

import ai.qorva.core.exception.QorvaException;
import com.stripe.model.Event;

public interface StripeEventHandler {
	void handle(Event event) throws QorvaException;
}
