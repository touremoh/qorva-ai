package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum ApplicationStatusEnum {
	NEW("NEW"),
	OPEN("OPEN"),
	CLOSE("CLOSE"),
	SHORTLIST("SHORTLIST"),
	INTERVIEW("INTERVIEW"),
	REJECT("REJECT");

	ApplicationStatusEnum(String status) {
		this.status = status;
	}
	private final String status;
}
