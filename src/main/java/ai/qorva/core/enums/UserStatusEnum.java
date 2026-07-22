package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum UserStatusEnum {
	ACTIVE("ACTIVE"),
	INACTIVE("INACTIVE"),
	LOCKED("LOCKED"),
	DELETED("DELETED"),
	TRIAL_PERIOD("TRIAL_PERIOD"),
	PENDING_SUBSCRIPTION("PENDING_SUBSCRIPTION"),
	DEMO("DEMO");

	UserStatusEnum(String value) {
		this.value = value;
	}
	private final String value;
}
