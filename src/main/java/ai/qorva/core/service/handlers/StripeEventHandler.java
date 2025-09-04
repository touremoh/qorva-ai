package ai.qorva.core.service.handlers;

import ai.qorva.core.exception.QorvaException;
import com.stripe.model.StripeObject;

public interface StripeEventHandler {
	void handle(StripeObject obj) throws QorvaException;
}
