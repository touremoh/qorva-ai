package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum BillingCycle {
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY");

    BillingCycle(String value) {
        this.value = value;
    }
    private final String value;
}
