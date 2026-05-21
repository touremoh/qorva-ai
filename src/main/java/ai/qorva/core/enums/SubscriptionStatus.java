package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum SubscriptionStatus {
    PENDING_SUBSCRIPTION("pending_subscription"),
    ACTIVE("active"),
    PAST_DUE("past_due"),
    CANCELED("canceled"),
    INCOMPLETE("incomplete"),
    TRIALING("trialing"),
    PAUSED("paused");

    SubscriptionStatus(String value) {
        this.value = value;
    }
    private final String value;
}
