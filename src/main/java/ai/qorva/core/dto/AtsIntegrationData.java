package ai.qorva.core.dto;

import ai.qorva.core.dao.entity.AtsConnection;

import java.time.Instant;
import java.util.List;

/** API shapes for ATS integrations. Credentials never appear here in any form. */
public final class AtsIntegrationData {

	private AtsIntegrationData() {}

	/**
	 * authKind is the preferred flow; supportsApiKey and oauthAvailable say what the tenant
	 * can actually use right now — a provider may offer both, and OAuth only counts when a
	 * client is configured for this environment.
	 */
	public record ProviderView(
		String provider,
		String authKind,
		boolean supportsApiKey,
		boolean oauthAvailable,
		/* Signed providers verify an HMAC, so the tenant must paste Qorva's webhook secret
		 * into the ATS; the rest authenticate by the token already inside the webhook URL and
		 * have no secret to copy. Read from the same enum flag the connector checks. */
		boolean webhooksSigned,
		boolean available,
		boolean connected
	) {}

	/** zohoRegions lets the connect UI offer the datacenters the backend will accept. */
	public record ProviderCatalog(
		List<ProviderView> providers,
		int maxConnections,
		int usedConnections,
		List<String> zohoRegions
	) {}

	/**
	 * apiKey carries the single token most providers use; clientId/clientSecret are the
	 * Greenhouse Harvest v3 pair. Exactly one of the two shapes is required per provider.
	 */
	public record CreateRequest(
		String provider,
		String displayName,
		String apiKey,
		String clientId,
		String clientSecret,
		String subdomain,
		String companyId,
		String onBehalfOfUserId,
		/* Lever's account signing token, when the tenant supplies it by hand. */
		String webhookSigningSecret
	) {}

	public record UpdateRequest(
		String displayName,
		Boolean autoImport,
		Boolean importJobs,
		Boolean writeBackScores,
		Boolean initialSyncConfirmed,
		Boolean enabled,
		/* Lever's account signing token; setting it re-registers the webhooks. */
		String webhookSigningSecret
	) {}

	public record ConnectionView(
		String id,
		String provider,
		String displayName,
		String status,
		Boolean autoImport,
		Boolean importJobs,
		Boolean writeBackScores,
		Boolean initialSyncConfirmed,
		Instant lastSyncAt,
		String lastSyncError,
		/* What the tenant pastes into the ATS webhook settings. */
		String webhookUrl,
		String webhookSecret,
		/* True when Qorva creates this provider's webhooks itself, so the tab shows a status
		 * line instead of manual instructions. */
		boolean webhooksManaged,
		boolean webhooksRegistered,
		String webhookError,
		Instant createdAt
	) {
		public static ConnectionView from(AtsConnection c, String webhookUrl, boolean webhooksManaged) {
			var settings = c.getSettings() != null ? c.getSettings() : new AtsConnection.Settings();
			var sync = c.getSyncState() != null ? c.getSyncState() : new AtsConnection.SyncState();
			var hooks = c.getWebhookState();
			return new ConnectionView(
				c.getId(), c.getProvider(), c.getDisplayName(), c.getStatus(),
				settings.getAutoImport(), settings.getImportJobs(), settings.getWriteBackScores(),
				settings.getInitialSyncConfirmed(),
				sync.getLastSyncAt(), sync.getLastSyncError(),
				webhookUrl, c.getWebhookSecret(),
				webhooksManaged,
				hooks != null && hooks.getRegisteredAt() != null,
				hooks != null ? hooks.getLastError() : null,
				c.getCreatedAt());
		}
	}

	public record ConnectionList(List<ConnectionView> connections) {}

	/** region is the provider datacenter to authenticate against (Zoho only; null elsewhere). */
	public record OauthStartRequest(String provider, String region) {}

	public record OauthStartResponse(String consentUrl) {}

}
