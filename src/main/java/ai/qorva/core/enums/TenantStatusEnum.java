package ai.qorva.core.enums;

/**
 * A company's own status, set from the admin console — separate from the Stripe subscription status.
 * Absent on documents created before it existed: read as {@link #ACTIVE}.
 */
public enum TenantStatusEnum {
	ACTIVE,
	SUSPENDED,
	DELETED;

	public static TenantStatusEnum of(String value) {
		if (value == null || value.isBlank()) {
			return ACTIVE;
		}
		try {
			return valueOf(value);
		} catch (IllegalArgumentException e) {
			return ACTIVE;
		}
	}
}
