package ai.qorva.core.enums;

import lombok.Getter;

/**
 * Supported ATS providers. Auth kind drives both the connect UI (API-key form vs OAuth
 * redirect) and the connector's request signing. Base URLs are fixed per provider —
 * tenant input is limited to path segments (subdomain / company id), validated in
 * AtsConnectionService, so no tenant-controlled host ever reaches the HTTP client.
 *
 * <p>A provider can support both flows, and authKind names the one the connect UI offers.
 * Greenhouse and Lever are credential-form providers even though both speak OAuth on the
 * wire: Greenhouse Harvest v3 takes a client id and secret the customer creates themselves
 * and exchanges them for tokens with the client-credentials grant, and Lever takes an API
 * key a Super Admin generates. Neither needs partner approval, which is exactly why they
 * are preferred over the redirect flows their partner programs would require.</p>
 */
@Getter
public enum AtsProviderEnum {

	GREENHOUSE("greenhouse", AuthKind.API_KEY, true, true),
	RECRUITEE("recruitee", AuthKind.API_KEY, true, false),
	WORKABLE("workable", AuthKind.API_KEY, true, true),
	MANATAL("manatal", AuthKind.API_KEY, true, false),
	BAMBOOHR("bamboohr", AuthKind.API_KEY, true, false),
	ZOHO_RECRUIT("zoho_recruit", AuthKind.OAUTH2, false, false),
	LEVER("lever", AuthKind.API_KEY, true, true),
	ASHBY("ashby", AuthKind.API_KEY, true, true);

	public enum AuthKind { API_KEY, OAUTH2 }

	private final String value;

	/** Preferred connect flow; also what the connector signs with when both are possible. */
	private final AuthKind authKind;

	private final boolean apiKeySupported;

	private final boolean webhooksSigned;

	AtsProviderEnum(String value, AuthKind authKind, boolean apiKeySupported, boolean webhooksSigned) {
		this.value = value;
		this.authKind = authKind;
		this.apiKeySupported = apiKeySupported;
		this.webhooksSigned = webhooksSigned;
	}

	/** True when a tenant can connect by pasting a key they generated themselves. */
	public boolean supportsApiKey() {
		return apiKeySupported;
	}

	/** True when the provider has an OAuth flow; usability also needs a configured client id. */
	public boolean supportsOauth() {
		return authKind == AuthKind.OAUTH2;
	}

	/**
	 * True when the provider signs webhook payloads, so authenticity is proven by the body
	 * signature inside the connector. The rest authenticate by the secret token in the URL,
	 * which is why the webhook URL shown to the tenant differs. Both the URL we hand out and
	 * the check we run on delivery read this one flag — they must never disagree.
	 */
	public boolean signsWebhooks() {
		return webhooksSigned;
	}

	public static AtsProviderEnum fromValue(String value) {
		for (var provider : values()) {
			if (provider.value.equalsIgnoreCase(value)) {
				return provider;
			}
		}
		throw new IllegalArgumentException("Unknown ATS provider: " + value);
	}
}
