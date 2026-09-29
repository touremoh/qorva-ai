package ai.qorva.core.security;

import ai.qorva.core.enums.SubscriptionStatus;
import lombok.experimental.UtilityClass;

import java.util.Set;

/**
 * The subscription statuses that block use of the application. Shared by the request filter (which
 * reads the status from the token) and background work acting for a user (which reads it from the tenant).
 */
@UtilityClass
public class SubscriptionGate {

	private static final Set<String> BLOCKED_STATUSES = Set.of(
		SubscriptionStatus.CANCELED.getValue(),
		SubscriptionStatus.PAST_DUE.getValue()
	);

	public static boolean blocks(String subscriptionStatus) {
		return subscriptionStatus != null && BLOCKED_STATUSES.contains(subscriptionStatus);
	}
}
