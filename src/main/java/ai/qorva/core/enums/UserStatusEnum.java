package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum UserStatusEnum {
	ACTIVE("ACTIVE"),
	INACTIVE("INACTIVE"),
	LOCKED("LOCKED"),
	TRIAL_PERIOD("TRIAL_PERIOD");

	UserStatusEnum(String value) {
		this.value = value;
	}
	private final String value;
}
