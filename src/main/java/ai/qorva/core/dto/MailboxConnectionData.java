package ai.qorva.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** API shapes for {@code /mailbox-connections}. Token material never appears here. */
public final class MailboxConnectionData {

	private MailboxConnectionData() {}

	public record View(String provider, String emailAddress, String status, Instant connectedAt, Instant lastUsedAt) {}

	@Getter
	@Setter
	@NoArgsConstructor
	public static class OauthStartRequest {
		/** {@link ai.qorva.core.enums.MailboxProviderEnum} name. */
		@NotBlank
		private String provider;
	}

	public record OauthStartResponse(String consentUrl) {}

	/** Which providers this environment can connect, so the UI only offers configured ones. */
	public record Availability(boolean microsoft) {}
}
