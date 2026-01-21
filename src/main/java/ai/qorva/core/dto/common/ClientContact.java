package ai.qorva.core.dto.common;

public record ClientContact (
	String contactId,
	String firstName,
	String lastName,
	String email,
	String phone,
	String role,
	Boolean isPrimary
) {}
