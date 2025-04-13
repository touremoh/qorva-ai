package ai.qorva.core.enums;

import lombok.Getter;

@Getter
public enum DashboardAccessType {
    FULL("FULL"),
    LIMITED("LIMITED");

	DashboardAccessType(String value) {
		this.value = value;
	}
    private final String value;
}
