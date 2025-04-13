package ai.qorva.core.enums;

import lombok.Getter;

import static java.lang.Integer.MAX_VALUE;

@Getter
public enum SubscriptionPlanEnum {
    FREE_TRIAL("FREE_TRIAL", MAX_VALUE),
    STARTER("STARTER", 500),
    GROWTH("GROWTH", 2000),
    PROFESSIONAL("PROFESSIONAL", 5000),
    ENTERPRISE("ENTERPRISE", MAX_VALUE);

    SubscriptionPlanEnum(String name, Integer limit) {
        this.name = name;
		this.limit = limit;
	}
    private final String name;
    private final Integer limit;
}
