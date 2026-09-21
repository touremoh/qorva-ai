package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connected-mailbox settings ({@code qorva.mailbox.*}). credentialsKey encrypts the stored OAuth
 * tokens (its own key, so it rotates independently of the ATS one). The Microsoft client is a
 * multi-tenant Entra app registration with delegated {@code Mail.Send}; a blank client id means
 * the feature is not enabled on this environment and connect attempts answer 503.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qorva.mailbox")
public class MailboxProperties {

	private String credentialsKey;
	private Microsoft microsoft = new Microsoft();

	@Getter
	@Setter
	public static class Microsoft {
		private String clientId;
		private String clientSecret;
		/** {@code https://login.microsoftonline.com/common} (any org + personal) or {@code …/organizations}. */
		private String authority = "https://login.microsoftonline.com/common";

		public boolean isConfigured() {
			return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
		}
	}
}
