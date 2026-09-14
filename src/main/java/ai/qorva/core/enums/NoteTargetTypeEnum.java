package ai.qorva.core.enums;

import lombok.Getter;

/** What a recruiter note is attached to. Drives which CV/report authorities gate the note. */
@Getter
public enum NoteTargetTypeEnum {
	CV(UserActionsEnum.VIEW_CV, UserActionsEnum.MODIFY_CV),
	MATCHING_REPORT(UserActionsEnum.VIEW_REPORT, UserActionsEnum.MODIFY_REPORT);

	private final UserActionsEnum readAction;
	private final UserActionsEnum writeAction;

	NoteTargetTypeEnum(UserActionsEnum readAction, UserActionsEnum writeAction) {
		this.readAction = readAction;
		this.writeAction = writeAction;
	}

	/** Case-insensitive parse; null when the value is unknown so callers can answer 400. */
	public static NoteTargetTypeEnum fromValue(String value) {
		if (value == null) return null;
		for (var type : values()) {
			if (type.name().equalsIgnoreCase(value.trim())) return type;
		}
		return null;
	}
}
