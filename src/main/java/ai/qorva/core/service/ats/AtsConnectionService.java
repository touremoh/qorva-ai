package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.dao.repository.AtsOutboundTaskRepository;
import ai.qorva.core.dto.AtsIntegrationData;
import ai.qorva.core.dto.AtsIntegrationData.ConnectionView;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import ai.qorva.core.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lifecycle of a tenant's ATS connections: plan-limited creation, credential
 * encryption, validation against the live provider, settings, and deletion.
 * Imported CVs survive a deletion — only the link and queued write-backs go.
 */
@Slf4j
@Service
public class AtsConnectionService {

	/**
	 * Fallback when no plan can be resolved at all — a workspace with no subscription
	 * (demo/trial) gets none. Every paid plan includes ATS connections, so a tenant on a
	 * plan must never land here: the plan config is consulted before giving up.
	 */
	public static final int DEFAULT_MAX_CONNECTIONS = 0;

	/** Subdomains / company ids are path segments on fixed hosts — never a full URL. */
	private static final Pattern PATH_SEGMENT = Pattern.compile("^[a-zA-Z0-9-]{1,63}$");

	private final AtsConnectionRepository connectionRepository;
	private final AtsOutboundTaskRepository outboundTaskRepository;
	private final AtsConnectorRegistry registry;
	private final CredentialsCipher cipher;
	private final AtsProperties properties;
	private final TenantService tenantService;
	private final ProductReferenceService productReferenceService;
	private final QorvaProductProperties productProperties;
	private final SecureRandom random = new SecureRandom();

	public AtsConnectionService(
		AtsConnectionRepository connectionRepository,
		AtsOutboundTaskRepository outboundTaskRepository,
		AtsConnectorRegistry registry,
		CredentialsCipher cipher,
		AtsProperties properties,
		TenantService tenantService,
		ProductReferenceService productReferenceService,
		QorvaProductProperties productProperties
	) {
		this.connectionRepository = connectionRepository;
		this.outboundTaskRepository = outboundTaskRepository;
		this.registry = registry;
		this.cipher = cipher;
		this.properties = properties;
		this.tenantService = tenantService;
		this.productReferenceService = productReferenceService;
		this.productProperties = productProperties;
	}

	/**
	 * Max simultaneous connections for this tenant's plan. The Stripe product reference is
	 * the primary source, but it only carries limits once the catalog sync has run and the
	 * Stripe product name matched a configured plan. Falling straight through to zero there
	 * would tell a paying tenant their plan excludes ATS, so the configured plan limits —
	 * the same values the sync writes into Mongo — answer when the reference cannot.
	 */
	public int maxConnectionsForTenant(String tenantId) {
		try {
			var sub = tenantService.findOneById(tenantId).getSubscriptionInfo();
			if (sub == null) {
				return DEFAULT_MAX_CONNECTIONS;
			}
			if (StringUtils.hasText(sub.getPriceId())) {
				var product = productReferenceService.findByStripePriceId(sub.getPriceId());
				if (product != null && product.getFeatures() != null && product.getFeatures().getLimits() != null
					&& product.getFeatures().getLimits().getAtsConnections() != null) {
					return product.getFeatures().getLimits().getAtsConnections();
				}
			}
			var configured = configuredConnectionsFor(sub.getSubscriptionPlan());
			if (configured != null) {
				log.debug("ATS connection limit for tenant {} resolved from plan config ({})",
					tenantId, sub.getSubscriptionPlan());
				return configured;
			}
		} catch (Exception e) {
			log.warn("Could not resolve ATS connection limit for tenant {}: {}", tenantId, e.getMessage());
		}
		return DEFAULT_MAX_CONNECTIONS;
	}

	/** Plan limit straight from configuration, matched on the Stripe product name. */
	private Integer configuredConnectionsFor(String subscriptionPlan) {
		if (!StringUtils.hasText(subscriptionPlan)) {
			return null;
		}
		return Stream.of(productProperties.getStarter(), productProperties.getPro(), productProperties.getScale())
			.filter(plan -> plan != null && plan.getStripeProductName() != null
				&& plan.getStripeProductName().equalsIgnoreCase(subscriptionPlan.trim()))
			.map(QorvaProductProperties.ProductPlanConfig::getFeatures)
			.filter(features -> features != null && features.getLimits() != null)
			.map(features -> features.getLimits().getAtsConnections())
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	public AtsIntegrationData.ProviderCatalog catalog(String tenantId) {
		var connections = connectionRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
		var providers = new ArrayList<AtsIntegrationData.ProviderView>();
		int max = maxConnectionsForTenant(tenantId);
		for (var provider : AtsProviderEnum.values()) {
			boolean connected = connections.stream().anyMatch(c -> c.getProvider().equals(provider.getValue()));
			providers.add(new AtsIntegrationData.ProviderView(
				provider.getValue(), provider.getAuthKind().name(),
				provider.supportsApiKey(), oauthAvailable(provider),
				max > connections.size() || connected, connected));
		}
		return new AtsIntegrationData.ProviderCatalog(
			providers, max, connections.size(), AtsOauthService.zohoRegions());
	}

	/**
	 * OAuth is only offered once a client is registered for the provider — otherwise the
	 * consent call would 501 and the tenant would be stuck with no way to connect at all.
	 */
	private boolean oauthAvailable(AtsProviderEnum provider) {
		if (!provider.supportsOauth()) {
			return false;
		}
		var client = properties.getOauth().get(provider.getValue());
		return client != null && StringUtils.hasText(client.getClientId());
	}

	public AtsIntegrationData.ConnectionList list(String tenantId) {
		var views = connectionRepository.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
			.map(c -> ConnectionView.from(c, webhookUrl(c)))
			.toList();
		return new AtsIntegrationData.ConnectionList(views);
	}

	public ConnectionView create(String tenantId, AtsIntegrationData.CreateRequest request, String createdBy)
		throws QorvaException {
		var provider = parseProvider(request.provider());
		if (!provider.supportsApiKey()) {
			throw badRequest(QorvaErrorCodes.ATS_PROVIDER_UNKNOWN);
		}
		if (!StringUtils.hasText(request.apiKey())) {
			throw badRequest(QorvaErrorCodes.HTTP_VALIDATION);
		}
		assertPathSegment(request.subdomain());
		assertPathSegment(request.companyId());
		requireProviderFields(provider, request);
		assertCreatable(tenantId, provider);

		var credentials = AtsCredentials.builder()
			.apiKey(request.apiKey().trim())
			.subdomain(trimOrNull(request.subdomain()))
			.companyId(trimOrNull(request.companyId()))
			.onBehalfOfUserId(trimOrNull(request.onBehalfOfUserId()))
			.build();
		registry.get(provider).validate(credentials);

		var connection = save(tenantId, provider, request.displayName(), credentials, createdBy);
		log.info("ATS connection {} ({}) created for tenant {}", connection.getId(), provider, tenantId);
		return ConnectionView.from(connection, webhookUrl(connection));
	}

	/** OAuth callback path: credentials already exchanged and validated upstream. */
	public AtsConnection createFromOauth(String tenantId, AtsProviderEnum provider, AtsCredentials credentials)
		throws QorvaException {
		assertCreatable(tenantId, provider);
		registry.get(provider).validate(credentials);
		return save(tenantId, provider, null, credentials, "oauth-callback");
	}

	private void assertCreatable(String tenantId, AtsProviderEnum provider) throws QorvaException {
		if (connectionRepository.existsByTenantIdAndProvider(tenantId, provider.getValue())) {
			throw new QorvaException(QorvaErrorCodes.ATS_CONNECTION_EXISTS,
				HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
		}
		int max = maxConnectionsForTenant(tenantId);
		if (connectionRepository.countByTenantId(tenantId) >= max) {
			throw new QorvaException(QorvaErrorCodes.ATS_CONNECTION_LIMIT_FOR_PLAN,
				HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN, max);
		}
	}

	private AtsConnection save(String tenantId, AtsProviderEnum provider, String displayName,
		AtsCredentials credentials, String createdBy) throws QorvaException {
		var secret = new byte[24];
		random.nextBytes(secret);
		return connectionRepository.save(AtsConnection.builder()
			.tenantId(tenantId)
			.provider(provider.getValue())
			.displayName(StringUtils.hasText(displayName) ? displayName.trim() : null)
			.status(AtsConnection.STATUS_CONNECTED)
			.encryptedCredentials(cipher.encrypt(credentials))
			.settings(AtsConnection.Settings.builder()
				.autoImport(true)
				.importJobs(true)
				.writeBackScores(false)
				.initialSyncConfirmed(false)
				.build())
			.syncState(new AtsConnection.SyncState())
			.webhookSecret(Base64.getUrlEncoder().withoutPadding().encodeToString(secret))
			.createdBy(createdBy)
			.build());
	}

	public ConnectionView update(String tenantId, String connectionId, AtsIntegrationData.UpdateRequest request)
		throws QorvaException {
		var connection = findOwned(tenantId, connectionId);
		if (request.displayName() != null) {
			connection.setDisplayName(request.displayName().isBlank() ? null : request.displayName().trim());
		}
		var settings = connection.getSettings() != null ? connection.getSettings() : new AtsConnection.Settings();
		if (request.autoImport() != null) settings.setAutoImport(request.autoImport());
		if (request.importJobs() != null) settings.setImportJobs(request.importJobs());
		if (request.writeBackScores() != null) settings.setWriteBackScores(request.writeBackScores());
		if (request.initialSyncConfirmed() != null) settings.setInitialSyncConfirmed(request.initialSyncConfirmed());
		connection.setSettings(settings);
		if (request.enabled() != null && !AtsConnection.STATUS_AUTH_ERROR.equals(connection.getStatus())) {
			connection.setStatus(request.enabled() ? AtsConnection.STATUS_CONNECTED : AtsConnection.STATUS_DISABLED);
		}
		connectionRepository.save(connection);
		return ConnectionView.from(connection, webhookUrl(connection));
	}

	public ConnectionView test(String tenantId, String connectionId) throws QorvaException {
		var connection = findOwned(tenantId, connectionId);
		try {
			registry.get(AtsProviderEnum.fromValue(connection.getProvider()))
				.validate(cipher.decrypt(connection.getEncryptedCredentials()));
			if (AtsConnection.STATUS_AUTH_ERROR.equals(connection.getStatus())) {
				connection.setStatus(AtsConnection.STATUS_CONNECTED);
			}
		} catch (QorvaException e) {
			connection.setStatus(AtsConnection.STATUS_AUTH_ERROR);
			connectionRepository.save(connection);
			throw e;
		}
		connectionRepository.save(connection);
		return ConnectionView.from(connection, webhookUrl(connection));
	}

	public void delete(String tenantId, String connectionId) throws QorvaException {
		var connection = findOwned(tenantId, connectionId);
		outboundTaskRepository.deleteByConnectionId(connection.getId());
		connectionRepository.delete(connection);
		log.info("ATS connection {} deleted for tenant {} (imported CVs kept)", connectionId, tenantId);
	}

	public AtsConnection findOwned(String tenantId, String connectionId) throws QorvaException {
		return connectionRepository.findByIdAndTenantId(connectionId, tenantId)
			.orElseThrow(() -> new QorvaException(QorvaErrorCodes.ATS_CONNECTION_NOT_FOUND,
				HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));
	}

	public AtsCredentials decryptCredentials(AtsConnection connection) throws QorvaException {
		return cipher.decrypt(connection.getEncryptedCredentials());
	}

	public void markAuthError(AtsConnection connection, String message) {
		connection.setStatus(AtsConnection.STATUS_AUTH_ERROR);
		var sync = connection.getSyncState() != null ? connection.getSyncState() : new AtsConnection.SyncState();
		sync.setLastSyncError(message);
		connection.setSyncState(sync);
		connectionRepository.save(connection);
	}

	/**
	 * Signing providers verify the body signature inside the connector; the rest authenticate
	 * by the token in the URL itself, so only they get it appended.
	 */
	public String webhookUrl(AtsConnection connection) {
		var base = properties.getPublicBaseUrl() + "/public/ats/webhooks/" + connection.getId();
		var provider = AtsProviderEnum.fromValue(connection.getProvider());
		return provider.signsWebhooks() ? base : base + "?token=" + connection.getWebhookSecret();
	}

	private void requireProviderFields(AtsProviderEnum provider, AtsIntegrationData.CreateRequest request)
		throws QorvaException {
		boolean missing = switch (provider) {
			case WORKABLE, BAMBOOHR -> !StringUtils.hasText(request.subdomain());
			case RECRUITEE -> !StringUtils.hasText(request.companyId());
			default -> false;
		};
		if (missing) {
			throw badRequest(QorvaErrorCodes.HTTP_VALIDATION);
		}
	}

	private void assertPathSegment(String value) throws QorvaException {
		if (StringUtils.hasText(value) && !PATH_SEGMENT.matcher(value.trim()).matches()) {
			throw badRequest(QorvaErrorCodes.HTTP_VALIDATION);
		}
	}

	private AtsProviderEnum parseProvider(String value) throws QorvaException {
		try {
			return AtsProviderEnum.fromValue(value);
		} catch (Exception e) {
			throw badRequest(QorvaErrorCodes.ATS_PROVIDER_UNKNOWN);
		}
	}

	private QorvaException badRequest(String code) {
		return new QorvaException(code, HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
	}

	private String trimOrNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
