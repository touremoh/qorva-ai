package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum UserAuthoritiesEnum {
	ALLOWED("ALLOWED"),
  	NOT_ALLOWED("NOT_ALLOWED");

	UserAuthoritiesEnum(String value) {
		this.value = value;
	}

	private final String value;
}
