package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.dao.repository.AtsOutboundTaskRepository;
import ai.qorva.core.dto.AtsIntegrationData;
import ai.qorva.core.dto.ProductReferenceDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.common.FeatureLimits;
import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import ai.qorva.core.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtsConnectionServiceTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";

	@Mock private AtsConnectionRepository connectionRepository;
	@Mock private AtsOutboundTaskRepository outboundTaskRepository;
	@Mock private AtsConnectorRegistry registry;
	@Mock private AtsConnector connector;
	@Mock private TenantService tenantService;
	@Mock private ProductReferenceService productReferenceService;

	private AtsConnectionService service;
	private AtsProperties properties;
	private QorvaProductProperties productProperties;

	@BeforeEach
	void setUp() {
		properties = new AtsProperties();
		properties.setCredentialsKey("unit-test-key");
		properties.setPublicBaseUrl("https://api.qorva.test");
		productProperties = new QorvaProductProperties();
		productProperties.setStarter(plan("Starter", 1));
		productProperties.setPro(plan("Pro", 3));
		productProperties.setScale(plan("Scale", 7));
		var cipher = new CredentialsCipher(properties, new ObjectMapper().findAndRegisterModules());
		// A real webhook service, not a mock: it owns webhookUrl, and the mocked connector
		// reports no registration support, so nothing reaches a provider.
		var webhookService = new AtsWebhookService(connectionRepository, registry, cipher, properties);
		service = new AtsConnectionService(connectionRepository, outboundTaskRepository, registry,
			cipher, properties, tenantService, productReferenceService, productProperties, webhookService);
	}

	private static QorvaProductProperties.ProductPlanConfig plan(String stripeName, int atsConnections) {
		var features = new ProductFeatures();
		features.setLimits(FeatureLimits.builder().atsConnections(atsConnections).build());
		return new QorvaProductProperties.ProductPlanConfig(stripeName, features);
	}

	/** Tenant on a plan whose Stripe product reference carries no limits (catalog not synced). */
	private void givenPlanWithoutProductReference(String planName) throws QorvaException {
		var tenant = new TenantDTO();
		var sub = new SubscriptionInfo();
		sub.setSubscriptionPlan(planName);
		tenant.setSubscriptionInfo(sub);
		lenient().when(tenantService.findOneById(TENANT)).thenReturn(tenant);
	}

	private void givenPlanCap(Integer cap) throws QorvaException {
		var tenant = new TenantDTO();
		var sub = new SubscriptionInfo();
		sub.setPriceId("price_1");
		tenant.setSubscriptionInfo(sub);
		lenient().when(tenantService.findOneById(TENANT)).thenReturn(tenant);
		var product = new ProductReferenceDTO();
		var features = new ProductFeatures();
		features.setLimits(FeatureLimits.builder().atsConnections(cap).build());
		product.setFeatures(features);
		lenient().when(productReferenceService.findByStripePriceId("price_1")).thenReturn(product);
	}

	/** Manatal is the simplest API-key provider: key only, no subdomain or company id. */
	private AtsIntegrationData.CreateRequest apiKeyRequest() {
		return new AtsIntegrationData.CreateRequest("manatal", null, "mt-key", null, null, null, null, null, null);
	}

	@Test
	void createRejectedWhenPlanHasNoConnections() throws QorvaException {
		givenPlanCap(0);
		when(connectionRepository.existsByTenantIdAndProvider(TENANT, "manatal")).thenReturn(false);
		when(connectionRepository.countByTenantId(TENANT)).thenReturn(0L);

		assertThatThrownBy(() -> service.create(TENANT, apiKeyRequest(), "user"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.ATS_CONNECTION_LIMIT_FOR_PLAN);
		verify(connectionRepository, never()).save(any());
	}

	@Test
	void createRejectedWhenProviderAlreadyConnected() throws QorvaException {
		givenPlanCap(3);
		when(connectionRepository.existsByTenantIdAndProvider(TENANT, "manatal")).thenReturn(true);

		assertThatThrownBy(() -> service.create(TENANT, apiKeyRequest(), "user"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.ATS_CONNECTION_EXISTS);
	}

	@Test
	void createValidatesAgainstProviderAndStoresEncryptedCredentials() throws QorvaException {
		givenPlanCap(3);
		when(connectionRepository.existsByTenantIdAndProvider(TENANT, "manatal")).thenReturn(false);
		when(connectionRepository.countByTenantId(TENANT)).thenReturn(0L);
		when(registry.get(AtsProviderEnum.MANATAL)).thenReturn(connector);
		when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var view = service.create(TENANT, apiKeyRequest(), "user");

		verify(connector).validate(any(AtsCredentials.class));
		assertThat(view.provider()).isEqualTo("manatal");
		assertThat(view.status()).isEqualTo(AtsConnection.STATUS_CONNECTED);
		// Secrets must never leak into the view, and the stored blob must not be plaintext.
		assertThat(view.toString()).doesNotContain("mt-key");
		verify(connectionRepository).save(org.mockito.ArgumentMatchers.argThat(c ->
			c.getEncryptedCredentials() != null && !c.getEncryptedCredentials().contains("mt-key")
				&& c.getWebhookSecret() != null));
	}

	@Test
	void subdomainMustBeAPlainPathSegment() throws QorvaException {
		var request = new AtsIntegrationData.CreateRequest(
			"bamboohr", null, "key", null, null, "evil.example.com/path", null, null, null);

		assertThatThrownBy(() -> service.create(TENANT, request, "user"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.HTTP_VALIDATION);
	}

	/** Zoho is the only provider left with no customer-generated credential to paste. */
	@Test
	void oauthOnlyProvidersCannotBeCreatedWithAnApiKey() {
		var request = new AtsIntegrationData.CreateRequest("zoho_recruit", null, "key", null, null, null, null, null, null);

		assertThatThrownBy(() -> service.create(TENANT, request, "user"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.ATS_PROVIDER_UNKNOWN);
		verify(connectionRepository, never()).save(any());
	}

	@Test
	void greenhouseAcceptsCustomerGeneratedHarvestV3ClientCredentials() throws QorvaException {
		givenPlanCap(3);
		when(connectionRepository.existsByTenantIdAndProvider(TENANT, "greenhouse")).thenReturn(false);
		when(connectionRepository.countByTenantId(TENANT)).thenReturn(0L);
		when(registry.get(AtsProviderEnum.GREENHOUSE)).thenReturn(connector);
		when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		var request = new AtsIntegrationData.CreateRequest(
			"greenhouse", null, null, "gh-client", "gh-secret", null, null, "4321", null);

		var view = service.create(TENANT, request, "user");

		verify(connector).validate(any(AtsCredentials.class));
		assertThat(view.provider()).isEqualTo("greenhouse");
		assertThat(view.status()).isEqualTo(AtsConnection.STATUS_CONNECTED);
		assertThat(view.toString()).doesNotContain("gh-secret");
	}

	@Test
	void catalogOffersOauthOnlyOnceAClientIsConfigured() {
		var zoho = catalogEntry("zoho_recruit");
		// No client id registered yet: the card must stay inert rather than show a dead button.
		assertThat(zoho.oauthAvailable()).isFalse();
		assertThat(zoho.supportsApiKey()).isFalse();

		var client = new AtsProperties.OauthClient();
		client.setClientId("zoho-client");
		properties.getOauth().put("zoho_recruit", client);
		assertThat(catalogEntry("zoho_recruit").oauthAvailable()).isTrue();
	}

	/**
	 * Greenhouse and Lever both speak OAuth on the wire but connect through the credentials
	 * form, so neither may advertise a consent redirect the UI would prefer over the form.
	 */
	@Test
	void credentialFormProvidersNeverOfferOauth() {
		for (String provider : new String[]{"greenhouse", "lever", "manatal", "ashby"}) {
			assertThat(catalogEntry(provider).supportsApiKey()).as(provider).isTrue();
			assertThat(catalogEntry(provider).oauthAvailable()).as(provider).isFalse();
		}
	}

	@Test
	void everyPlanGetsItsConfiguredConnectionsEvenBeforeTheStripeCatalogSyncs() throws QorvaException {
		// The product reference is the primary source; without it a paying tenant must not be
		// told their plan excludes ATS.
		givenPlanWithoutProductReference("Starter");
		assertThat(service.maxConnectionsForTenant(TENANT)).isEqualTo(1);

		givenPlanWithoutProductReference("Pro");
		assertThat(service.maxConnectionsForTenant(TENANT)).isEqualTo(3);

		givenPlanWithoutProductReference("scale");   // plan names are matched case-insensitively
		assertThat(service.maxConnectionsForTenant(TENANT)).isEqualTo(7);
	}

	@Test
	void aWorkspaceWithNoSubscriptionStillGetsNone() throws QorvaException {
		var tenant = new TenantDTO();
		when(tenantService.findOneById(TENANT)).thenReturn(tenant);

		assertThat(service.maxConnectionsForTenant(TENANT))
			.isEqualTo(AtsConnectionService.DEFAULT_MAX_CONNECTIONS);
	}

	@Test
	void theProductReferenceStillWinsWhenItCarriesALimit() throws QorvaException {
		// Stripe stays authoritative: an override there must not be masked by the config.
		givenPlanCap(5);
		var tenant = new TenantDTO();
		var sub = new SubscriptionInfo();
		sub.setPriceId("price_1");
		sub.setSubscriptionPlan("Starter");
		tenant.setSubscriptionInfo(sub);
		when(tenantService.findOneById(TENANT)).thenReturn(tenant);

		assertThat(service.maxConnectionsForTenant(TENANT)).isEqualTo(5);
	}

	private AtsIntegrationData.ProviderView catalogEntry(String provider) {
		return service.catalog(TENANT).providers().stream()
			.filter(p -> p.provider().equals(provider))
			.findFirst()
			.orElseThrow();
	}

	@Test
	void webhookUrlEmbedsTokenOnlyForProvidersThatDoNotSign() {
		var recruitee = AtsConnection.builder().id("c1").provider("recruitee").webhookSecret("s3cret").build();
		var greenhouse = AtsConnection.builder().id("c2").provider("greenhouse").webhookSecret("s3cret").build();
		var ashby = AtsConnection.builder().id("c3").provider("ashby").webhookSecret("s3cret").build();

		assertThat(service.webhookUrl(recruitee))
			.isEqualTo("https://api.qorva.test/public/ats/webhooks/c1?token=s3cret");
		assertThat(service.webhookUrl(greenhouse))
			.isEqualTo("https://api.qorva.test/public/ats/webhooks/c2");
		assertThat(service.webhookUrl(ashby))
			.isEqualTo("https://api.qorva.test/public/ats/webhooks/c3");
	}

	@Test
	void theUrlHandedOutMatchesHowDeliveryIsAuthenticated() {
		// One flag drives both sides; a provider whose URL carries no token must be one the
		// connector can authenticate by signature, or its webhooks are unverifiable.
		for (var provider : AtsProviderEnum.values()) {
			var connection = AtsConnection.builder()
				.id("c1").provider(provider.getValue()).webhookSecret("s3cret").build();

			assertThat(service.webhookUrl(connection).contains("token="))
				.as("token in URL for %s", provider)
				.isEqualTo(!provider.signsWebhooks());
		}
	}
}
