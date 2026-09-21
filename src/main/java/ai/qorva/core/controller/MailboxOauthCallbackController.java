package ai.qorva.core.controller;

import ai.qorva.core.enums.MailboxProviderEnum;
import ai.qorva.core.service.mailbox.MailboxConnectionService;
import ai.qorva.core.service.mailbox.MailboxOauthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Unauthenticated OAuth redirect target for connected mailboxes, registered with the provider as
 * {publicBaseUrl}/integrations/mailbox/{provider}/oauth/callback (same shape as the ATS one).
 * Nothing here is trusted beyond the HMAC-signed state, which carries tenant, user and provider;
 * a path provider that disagrees with it is rejected. Lands back on Account settings › Profile.
 */
@Slf4j
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
		String result;
		try {
			if (error != null || code == null || state == null) {
				result = "denied";
			} else {
				var claims = oauthService.validateState(state);
				if (MailboxProviderEnum.fromValue(provider) != claims.provider()) {
					throw new IllegalStateException("provider mismatch between callback path and state");
				}
				connectionService.createFromOauth(claims, code);
				result = "connected";
			}
		} catch (Exception e) {
			log.warn("Mailbox OAuth callback failed: {}", e.getMessage());
			result = "failed";
		}
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(appBaseUrl + "/app/settings?mailboxOauth=" + result))
			.build();
	}
}
