package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * ATS integration settings. credentialsKey encrypts stored provider credentials
 * (any non-empty string works — it is hashed to an AES key). publicBaseUrl is this
 * API's externally reachable origin, used to build webhook URLs and OAuth redirect
 * URIs handed to providers.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qorva.ats")
public class AtsProperties {

	private String credentialsKey;
	private String publicBaseUrl;

	/** Scheduler interval between automatic delta syncs per connection. */
	private int syncIntervalMinutes = 30;

	/** First sync stops for confirmation above this many candidates. */
	private int initialSyncGuard = 500;

	/** Greenhouse Harvest base URL — configurable so a version bump needs no rebuild. */
	private String greenhouseHarvestBaseUrl = "https://harvest.greenhouse.io/v3";

	/**
	 * Where Greenhouse mints Harvest v3 bearer tokens from a tenant's own client id and
	 * secret. This is auth.greenhouse.io, deliberately not the api.greenhouse.io host the
	 * partner authorization-code flow uses — they are different endpoints.
	 */
	private String greenhouseTokenUrl = "https://auth.greenhouse.io/token";

	/** OAuth client registrations keyed by provider value (greenhouse, zoho_recruit, lever). */
	private Map<String, OauthClient> oauth = new HashMap<>();

	@Getter
	@Setter
	public static class OauthClient {
		private String clientId;
		private String clientSecret;

		/**
		 * Redirect URI registered with the provider. Must match byte for byte what the
		 * provider has on file, so it is configurable; empty falls back to
		 * {publicBaseUrl}/integrations/{provider}/oauth/callback.
		 */
		private String redirectUri;

		/**
		 * Space-separated scopes requested at consent, overriding the connector's default
		 * set. Register these in the provider's app settings first — an unapproved scope
		 * fails the whole authorization, so this stays configurable per environment.
		 */
		private String scopes;
	}
}
