package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum UserRoleEnum {
	OWNER("OWNER"),
	MAINTAINER("MAINTAINER");

	UserRoleEnum(String value) {
		this.value = value;
	}
	private final String value;
}
