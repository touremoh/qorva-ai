package ai.qorva.core.dto.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.Decimal128;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class SubscriptionInfo {

    private String subscriptionPlan;
    private String billingCycle;
    private Decimal128 price;

    private String subscriptionStatus;
    private String subscriptionId;

    private String accountManager;
    private Instant subscriptionStartDate;
    private Instant subscriptionEndDate;
}
