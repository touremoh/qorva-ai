package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum UserRoleEnum {
	ACCOUNT_OWNER("ACCOUNT_OWNER"),
	ACCOUNT_MANAGER("ACCOUNT_MANAGER");

	UserRoleEnum(String value) {
		this.value = value;
	}
	private final String value;
}
