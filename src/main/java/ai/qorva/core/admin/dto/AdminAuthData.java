package ai.qorva.core.admin.dto;

import ai.qorva.core.dao.entity.PlatformAdmin;

import java.time.Instant;

/** Requests and responses of the admin sign-in and admin management routes. */
public final class AdminAuthData {

	private AdminAuthData() {
	}

	public record LoginRequest(String email, String password) {}

	public record VerifyRequest(String challengeId, String code) {}

	public record ResendRequest(String challengeId) {}

	public record SetPasswordRequest(String token, String password) {}

	public record Challenge(String challengeId, String maskedEmail, Instant expiresAt, Instant resendAvailableAt) {}

	public record Session(String accessToken, Instant expiresAt, Admin admin) {}

	public record Admin(String id, String email, String firstName, String lastName, String role, String status,
	                    boolean passwordSet, Instant createdAt, Instant lastLoginAt) {
		public static Admin from(PlatformAdmin a) {
			return new Admin(a.getId(), a.getEmail(), a.getFirstName(), a.getLastName(), a.getRole(), a.getStatus(),
				a.getEncryptedPassword() != null, a.getCreatedAt(), a.getLastLoginAt());
		}
	}

	public record CreateAdminRequest(String email, String firstName, String lastName, String role) {}

	/** Null leaves a field unchanged; a blank name clears it. */
	public record UpdateAdminRequest(String role, String status, String firstName, String lastName) {}

	/** The signed-in admin's own name. Null leaves a field unchanged; blank clears it. */
	public record UpdateProfileRequest(String firstName, String lastName) {}
}
