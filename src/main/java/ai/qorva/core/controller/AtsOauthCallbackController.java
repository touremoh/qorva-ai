package ai.qorva.core.controller;

import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.service.ats.AtsConnectionService;
import ai.qorva.core.service.ats.AtsOauthService;
import ai.qorva.core.security.TenantScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Unauthenticated OAuth redirect target, registered with each provider as
 * {publicBaseUrl}/integrations/{provider}/oauth/callback — providers match the redirect
 * URI byte for byte, so the provider is part of the path and each app registration gets
 * its own. The legacy shared path stays mapped for registrations made before the split.
 * Nothing here is trusted beyond the HMAC-signed state: it carries the tenant and the
 * provider, and a path provider that disagrees with it is rejected.
 */
@RestController
public class AtsOauthCallbackController {

	private final AtsConnectionService connectionService;
	private final AtsOauthService oauthService;
	private final String appBaseUrl;

	public AtsOauthCallbackController(
		AtsConnectionService connectionService,
		AtsOauthService oauthService,
		@Value("${weblink.appBaseUrl:}") String appBaseUrl
	) {
		this.connectionService = connectionService;
		this.oauthService = oauthService;
		this.appBaseUrl = appBaseUrl;
	}

	@GetMapping({"/integrations/{provider}/oauth/callback", "/public/ats/oauth/callback"})
	public ResponseEntity<Void> oauthCallback(
		@PathVariable(name = "provider", required = false) String provider,
		@RequestParam(name = "code", required = false) String code,
		@RequestParam(name = "state", required = false) String state,
		@RequestParam(name = "accounts-server", required = false) String accountsServer,
		@RequestParam(name = "error", required = false) String error
	) {
		return OauthCallbackRedirect.handle("ATS", error, code, state, appBaseUrl + "/?atsOauth=", () -> {
			var claims = oauthService.validateState(state);
			if (provider != null && AtsProviderEnum.fromValue(provider) != claims.provider()) {
				throw new IllegalStateException("provider mismatch between callback path and state");
			}
			// The signed state names the tenant: the connection is created in its scope.
			TenantScope.runAs(claims.tenantId(), () -> {
				var credentials = oauthService.exchangeCode(claims.provider(), code, accountsServer, claims.datacenter());
				connectionService.createFromOauth(claims.tenantId(), claims.provider(), credentials);
			});
		});
	}
}
