package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum MontlyUsageLimitCodeEnum {
	REACHED("REACHED"),
	NOT_REACHED("NOT_REACHED");

	MontlyUsageLimitCodeEnum(String value) {
		this.value = value;
	}
	private final String value;
}
