package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum UserAccountStatus {
    USER_ACTIVE("USER_ACTIVE"),
    USER_INACTIVE("USER_INACTIVE"),
    USER_LOCKED("USER_LOCKED");


    UserAccountStatus(String value) {
        this.value = value;
    }
	private final String value;
}
