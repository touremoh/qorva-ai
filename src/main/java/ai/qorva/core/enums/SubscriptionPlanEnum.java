package ai.qorva.core.enums;

import lombok.Getter;

import static java.lang.Integer.MAX_VALUE;

@Getter
public enum SubscriptionPlanEnum {
    FREE_TRIAL("FREE_TRIAL", MAX_VALUE),
    STARTER("Starter", 500),
    GROWTH("Growth", 1000),
    PROFESSIONAL("Professional", 3000),
    ENTERPRISE("Enterprise", MAX_VALUE);

    SubscriptionPlanEnum(String name, Integer limit) {
        this.name = name;
		this.limit = limit;
	}
    private final String name;
    private final Integer limit;
}
