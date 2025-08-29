package ai.qorva.core.dto.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventSubscriptionDeleted {

    private String id; // event id
    private String type; // "customer.subscription.deleted"
    private DataWrapper data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper {
        private Subscription object;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subscription {
        private String id; // subscriptionId
        private String customer; // Stripe customer ID
        private String status;
        private int quantity;

        @JsonProperty("plan")
        private Plan plan;

        @JsonProperty("canceled_at")
        private Long canceledAt;

        @JsonProperty("cancellation_details")
        private CancellationDetails cancellationDetails;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plan {
        private String id; // planId (price id)
        private String product;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CancellationDetails {
        private String comment;
        private String feedback;
        private String reason;
    }
}
