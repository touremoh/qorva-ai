package ai.qorva.core.enums;

/** Mail providers a recruiter can connect. GOOGLE is reserved (parked — see the outreach guide). */
public enum MailboxProviderEnum {
	MICROSOFT;

	public static MailboxProviderEnum fromValue(String value) {
		if (value == null) return null;
		for (var provider : values()) {
			if (provider.name().equalsIgnoreCase(value.trim())) return provider;
		}
		return null;
	}
}
