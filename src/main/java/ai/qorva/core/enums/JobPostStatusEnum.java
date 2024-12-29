package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum JobPostStatusEnum {
	OPEN("open"),
	CLOSED("closed");

	JobPostStatusEnum(String status) {
		this.status = status;
	}
	private final String status;
}
