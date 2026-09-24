package ai.qorva.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** API shapes for email MFA ({@code /auth/mfa}, {@code /users/me/mfa}). Codes never appear in a response. */
public final class MfaData {

	private MfaData() {}

	/** An open challenge: the client keeps {@code challengeId} and sends it back with the code. */
	public record Challenge(String challengeId, String maskedEmail, Instant expiresAt, Instant resendAvailableAt) {}

	public record Status(boolean enabled, String email) {}

	@Getter
	@Setter
	@NoArgsConstructor
	public static class VerifyRequest {
		@NotBlank
		private String challengeId;
		@NotBlank
		private String code;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	public static class ResendRequest {
		@NotBlank
		private String challengeId;
	}
}
