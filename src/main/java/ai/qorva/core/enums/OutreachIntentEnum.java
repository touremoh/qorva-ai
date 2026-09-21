package ai.qorva.core.enums;

/** What the recruiter wants the AI draft to do. The prompt carries one instruction block per intent. */
public enum OutreachIntentEnum {
	INTRO,
	INTERVIEW,
	FOLLOW_UP,
	KEEP_WARM,
	CUSTOM;

	public static OutreachIntentEnum fromValue(String value) {
		if (value == null) return null;
		for (var intent : values()) {
			if (intent.name().equalsIgnoreCase(value.trim())) return intent;
		}
		return null;
	}
}
