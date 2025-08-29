package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum ReportStatusEnum {
	TEMPORARY("TEMPORARY"),
	PERMANENT("PERMANENT");

	ReportStatusEnum(String status) {
		this.status = status;
	}
	private String status;
}
