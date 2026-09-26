package ai.qorva.core.enums;

/**
 * How a candidate outreach left Qorva. Hand-off values are recorded when the composer opened the
 * recruiter's own mail client with the message prefilled; {@code CONNECTED_*} values when Qorva
 * sent it through the recruiter's connected mailbox.
 */
public enum OutreachViaEnum {
	GMAIL(false),
	OUTLOOK_WEB(false),
	MAILTO(false),
	CONNECTED_MICROSOFT(true);

	private final boolean connected;

	OutreachViaEnum(boolean connected) {
		this.connected = connected;
	}

	/** Hand-off values only — the ones a client may report through {@code POST /candidate-outreach/external}. */
	public static OutreachViaEnum fromHandoffValue(String value) {
		if (value == null) return null;
		for (var via : values()) {
			if (!via.connected && via.name().equalsIgnoreCase(value.trim())) return via;
		}
		return null;
	}
}
