package ai.qorva.core.utils;

import ai.qorva.core.enums.SubscriptionStatus;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SubscriptionStatusHelper {
	public static String subscriptionFromCode(String subscriptionStatus) {
		return switch (subscriptionStatus) {
			case "incomplete" -> SubscriptionStatus.SUBSCRIPTION_INCOMPLETE.getValue();
			case "trialing" -> SubscriptionStatus.FREE_TRIAL_PERIOD_ACTIVE.getValue();
			case "active" -> SubscriptionStatus.SUBSCRIPTION_ACTIVE.getValue();
			case "past_due", "unpaid" -> SubscriptionStatus.SUBSCRIPTION_PAYMENT_FAILED.getValue();
			case "canceled" -> SubscriptionStatus.SUBSCRIPTION_CANCELLED.getValue();
			case "paused" -> SubscriptionStatus.SUBSCRIPTION_LOCKED.getValue();
			default -> subscriptionStatus;
		};
	}
}
