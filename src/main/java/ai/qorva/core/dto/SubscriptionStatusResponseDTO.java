package ai.qorva.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusResponseDTO {
    private String subscriptionStatus;
    private String subscriptionPlan;
    private Instant currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
}
