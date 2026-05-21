package ai.qorva.core.utils;

import ai.qorva.core.enums.SubscriptionStatus;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SubscriptionStatusHelper {
	public static String subscriptionFromCode(String subscriptionStatus) {
		return switch (subscriptionStatus) {
			case "incomplete", "incomplete_expired" -> SubscriptionStatus.INCOMPLETE.getValue();
			case "trialing" -> SubscriptionStatus.TRIALING.getValue();
			case "active" -> SubscriptionStatus.ACTIVE.getValue();
			case "past_due", "unpaid" -> SubscriptionStatus.PAST_DUE.getValue();
			case "canceled" -> SubscriptionStatus.CANCELED.getValue();
			case "paused" -> SubscriptionStatus.PAUSED.getValue();
			default -> subscriptionStatus;
		};
	}
}
