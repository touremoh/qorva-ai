package ai.qorva.core.dto;

/**
 * Login / refresh result. Either {@code jwt} + {@code user} (signed in), or only {@code mfa} when the
 * password was right but the account has email MFA on and a code is still owed.
 */
public record AuthResponse(JwtDTO jwt, UserDTO user, MfaData.Challenge mfa) {

	public AuthResponse(JwtDTO jwt, UserDTO user) {
		this(jwt, user, null);
	}

	public static AuthResponse mfaRequired(MfaData.Challenge challenge) {
		return new AuthResponse(null, null, challenge);
	}
}
