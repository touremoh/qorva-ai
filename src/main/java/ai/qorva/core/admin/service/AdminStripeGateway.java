package ai.qorva.core.admin.service;

import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionUpdateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** The only Stripe writes the console makes: stop or resume renewal at the end of the paid period. A seam for tests. */
@Slf4j
@Component
public class AdminStripeGateway {

	public void setCancelAtPeriodEnd(String subscriptionId, boolean cancel) throws QorvaException {
		try {
			Subscription.retrieve(subscriptionId)
				.update(SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(cancel).build());
			log.info("Stripe subscription {} cancel_at_period_end={}", subscriptionId, cancel);
		} catch (StripeException e) {
			log.error("Stripe refused cancel_at_period_end={} on {}", cancel, subscriptionId, e);
			throw QorvaErrors.of(QorvaErrorCodes.ADMIN_STRIPE_FAILED, e, HttpStatus.SERVICE_UNAVAILABLE);
		}
	}
}
