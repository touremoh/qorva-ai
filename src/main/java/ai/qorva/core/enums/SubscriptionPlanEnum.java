package ai.qorva.core.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

import static java.lang.Integer.MAX_VALUE;

@Getter
public enum SubscriptionPlanEnum {
    FREE_TRIAL("FREE_TRIAL", MAX_VALUE, 1),
    STARTER("Starter", 500, 3),
    PRO("Pro", 2000, 10),
    SCALE("Scale", 6000, 25),
    ENTERPRISE("Enterprise", MAX_VALUE, MAX_VALUE);

    SubscriptionPlanEnum(String name, Integer limit, Integer maxSeats) {
        this.name = name;
        this.limit = limit;
        this.maxSeats = maxSeats;
    }

    private final String name;

    /** Monthly CV analysis limit. */
    private final Integer limit;

    /** Maximum number of active user seats for this plan. */
    private final Integer maxSeats;

    /** Returns the plan whose name matches (case-insensitive), or empty if unknown. */
    public static Optional<SubscriptionPlanEnum> fromName(String name) {
        return Arrays.stream(values())
            .filter(p -> p.name.equalsIgnoreCase(name))
            .findFirst();
    }
}
