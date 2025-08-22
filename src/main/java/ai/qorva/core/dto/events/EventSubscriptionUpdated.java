package ai.qorva.core.dto.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventSubscriptionUpdated {

    private String id;           // event id
    private String type;         // "customer.subscription.updated"
    private DataWrapper data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataWrapper {
        private Subscription object;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subscription {
        private String id;        // subscriptionId
        private String customer;  // Stripe customer ID
        private String status;
        private Integer quantity; // nullable in some variants

        // Legacy location (may be absent in newer API shapes)
        private Plan plan;

        // Fallback source(s) for price/plan in newer payloads
        private Items items;

        /**
         * Handy helper: normalize to a single "plan/price id".
         * Tries plan.id -> items.data[0].price.id -> items.data[0].plan.id
         */
        public String getEffectivePlanId() {
            if (plan != null && plan.id != null) {
                return plan.id;
            }
            if (items != null && items.data != null && !items.data.isEmpty()) {
                Item item0 = items.data.get(0);
                if (item0.price != null && item0.price.id != null) {
                    return item0.price.id;
                }
                if (item0.plan != null && item0.plan.id != null) {
                    return item0.plan.id;
                }
            }
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plan {
        private String id;
        private String product;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {
        private List<Item> data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Plan plan;   // sometimes present
        private Price price; // often the canonical source now
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Price {
        private String id; // the price/plan identifier
    }
}
