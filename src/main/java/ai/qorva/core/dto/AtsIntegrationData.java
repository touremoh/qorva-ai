package ai.qorva.core.dto;

import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.enums.AtsProviderEnum;

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

	public record CreateRequest(
		String provider,
		String displayName,
		String apiKey,
		String subdomain,
		String companyId,
		String onBehalfOfUserId
	) {}

	public record UpdateRequest(
		String displayName,
		Boolean autoImport,
		Boolean importJobs,
		Boolean writeBackScores,
		Boolean initialSyncConfirmed,
		Boolean enabled
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
		Instant createdAt
	) {
		public static ConnectionView from(AtsConnection c, String webhookUrl) {
			var settings = c.getSettings() != null ? c.getSettings() : new AtsConnection.Settings();
			var sync = c.getSyncState() != null ? c.getSyncState() : new AtsConnection.SyncState();
			return new ConnectionView(
				c.getId(), c.getProvider(), c.getDisplayName(), c.getStatus(),
				settings.getAutoImport(), settings.getImportJobs(), settings.getWriteBackScores(),
				settings.getInitialSyncConfirmed(),
				sync.getLastSyncAt(), sync.getLastSyncError(),
				webhookUrl, c.getWebhookSecret(), c.getCreatedAt());
		}
	}

	public record ConnectionList(List<ConnectionView> connections) {}

	/** region is the provider datacenter to authenticate against (Zoho only; null elsewhere). */
	public record OauthStartRequest(String provider, String region) {}

	public record OauthStartResponse(String consentUrl) {}

	public record TestResponse(String status) {}

	public static String authKindOf(String provider) {
		return AtsProviderEnum.fromValue(provider).getAuthKind().name();
	}
}
