package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.AuthenticationService;
import ai.qorva.core.service.sso.MicrosoftSsoService;
import ai.qorva.core.utils.BuildApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * "Sign in with Microsoft" — public routes (no token yet). The browser goes start → Microsoft → callback → the
 * app's {@code /login?sso=<code>}, and the app trades the code for the session at {@code POST /exchange}. Every
 * failure lands back on the login page with {@code ?ssoError=<reason>}, never on a raw error page.
 */
@Slf4j
@RestController
@RequestMapping("/auth/sso")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class SsoController {

	private final MicrosoftSsoService microsoftSsoService;
	private final AuthenticationService authenticationService;
	private final String appBaseUrl;

	public SsoController(MicrosoftSsoService microsoftSsoService, AuthenticationService authenticationService,
	                     @Value("${weblink.appBaseUrl:}") String appBaseUrl) {
		this.microsoftSsoService = microsoftSsoService;
		this.authenticationService = authenticationService;
		this.appBaseUrl = appBaseUrl;
	}

	public record ExchangeRequest(String code) {
	}

	/** Whether the login page offers the button. */
	@GetMapping(path = "/availability", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Boolean>> availability() {
		return ResponseEntity.ok(Map.of("microsoft", microsoftSsoService.isAvailable()));
	}

	@GetMapping("/microsoft/start")
	public ResponseEntity<Void> start(@RequestParam(name = "email", required = false) String email) {
		try {
			return redirect(microsoftSsoService.authorizeUrl(email));
		} catch (QorvaException e) {
			return toLogin("ssoError", "not_configured");
		}
	}

	@GetMapping("/microsoft/callback")
	public ResponseEntity<Void> callback(@RequestParam(name = "code", required = false) String code,
	                                     @RequestParam(name = "state", required = false) String state,
	                                     @RequestParam(name = "error", required = false) String error) {
		if (error != null) {
			log.info("Microsoft sign-in ended by Microsoft: {}", error);
			return toLogin("ssoError", "access_denied".equals(error) ? "cancelled" : "failed");
		}
		try {
			return toLogin("sso", microsoftSsoService.complete(code, state));
		} catch (QorvaException e) {
			return toLogin("ssoError", reason(e.getMessage()));
		} catch (RuntimeException e) {
			log.warn("Microsoft sign-in callback failed", e);
			return toLogin("ssoError", "failed");
		}
	}

	/** The one-time code → the same answer as a password sign-in (token, user, tenant). */
	@PostMapping(path = "/exchange", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QorvaRequestResponse> exchange(@RequestBody ExchangeRequest request) throws QorvaException {
		return BuildApiResponse.from(authenticationService.exchangeSsoCode(request == null ? null : request.code()));
	}

	static String reason(String errorKey) {
		if (QorvaErrorCodes.AUTH_SSO_NO_ACCOUNT.equals(errorKey)) return "no_account";
		if (QorvaErrorCodes.AUTH_SSO_NOT_CONFIGURED.equals(errorKey)) return "not_configured";
		return "failed";
	}

	private ResponseEntity<Void> toLogin(String param, String value) {
		return redirect(appBaseUrl + "/login?" + param + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
	}

	private static ResponseEntity<Void> redirect(String location) {
		var headers = new HttpHeaders();
		headers.setLocation(URI.create(location));
		headers.setCacheControl("no-store");
		return new ResponseEntity<>(headers, HttpStatus.FOUND);
	}
}
