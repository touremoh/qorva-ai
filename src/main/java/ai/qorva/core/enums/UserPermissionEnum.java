package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum UserPermissionEnum {
	ALLOWED("ALLOWED"),
  	NOT_ALLOWED("NOT_ALLOWED");

	UserPermissionEnum(String value) {
		this.value = value;
	}

	private final String value;
}
