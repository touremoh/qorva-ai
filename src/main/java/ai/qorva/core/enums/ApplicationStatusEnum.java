package ai.qorva.core.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * Where a candidate stands on a job (one value per matching report), in pipeline order. Rejected and Withdrawn
 * can be reached from any step.
 */
@Getter
public enum ApplicationStatusEnum {
	NEW("NEW"),
	CONTACTED("CONTACTED"),
	SHORTLISTED("SHORTLISTED"),
	INTERVIEWING("INTERVIEWING"),
	OFFERED("OFFERED"),
	HIRED("HIRED"),
	REJECTED("REJECTED"),
	WITHDRAWN("WITHDRAWN");

	ApplicationStatusEnum(String status) {
		this.status = status;
	}
	private final String status;

	public static Optional<ApplicationStatusEnum> parse(String value) {
		if (value == null) {
			return Optional.empty();
		}
		var normalized = value.trim().toUpperCase();
		return Arrays.stream(values()).filter(s -> s.status.equals(normalized)).findFirst();
	}
}
