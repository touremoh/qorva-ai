package ai.qorva.core.controller;

import ai.qorva.core.enums.MailboxProviderEnum;
import ai.qorva.core.service.mailbox.MailboxConnectionService;
import ai.qorva.core.service.mailbox.MailboxOauthService;
import ai.qorva.core.security.TenantScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * Unauthenticated OAuth redirect target for connected mailboxes, registered with the provider as
 * {publicBaseUrl}/integrations/mailbox/{provider}/oauth/callback (same shape as the ATS one).
 * Nothing here is trusted beyond the HMAC-signed state, which carries tenant, user and provider;
 * a path provider that disagrees with it is rejected. Lands back on Account settings › Profile.
 */
@RestController
public class MailboxOauthCallbackController {

	private final MailboxConnectionService connectionService;
	private final MailboxOauthService oauthService;
	private final String appBaseUrl;

	public MailboxOauthCallbackController(
		MailboxConnectionService connectionService,
		MailboxOauthService oauthService,
		@Value("${weblink.appBaseUrl:}") String appBaseUrl
	) {
		this.connectionService = connectionService;
		this.oauthService = oauthService;
		this.appBaseUrl = appBaseUrl;
	}

	@GetMapping("/integrations/mailbox/{provider}/oauth/callback")
	public ResponseEntity<Void> oauthCallback(
		@PathVariable("provider") String provider,
		@RequestParam(name = "code", required = false) String code,
		@RequestParam(name = "state", required = false) String state,
		@RequestParam(name = "error", required = false) String error
	) {
		return OauthCallbackRedirect.handle("Mailbox", error, code, state, appBaseUrl + "/app/settings?mailboxOauth=", () -> {
			var claims = oauthService.validateState(state);
			if (MailboxProviderEnum.fromValue(provider) != claims.provider()) {
				throw new IllegalStateException("provider mismatch between callback path and state");
			}
			// The signed state names the tenant and user: the connection is created in that tenant's scope.
			TenantScope.runAs(claims.tenantId(), () -> connectionService.createFromOauth(claims, code));
		});
	}
}
