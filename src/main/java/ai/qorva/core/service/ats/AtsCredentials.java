package ai.qorva.core.service.ats;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Decrypted provider credentials. Lives only in memory during a request or sync run —
 * persisted exclusively as the encrypted blob on AtsConnection. Which fields are used
 * depends on the provider: apiKey for token providers, the OAuth trio for Zoho/Lever,
 * subdomain/companyId as path segments, onBehalfOfUserId for Greenhouse notes.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AtsCredentials {

	private String apiKey;

	/** BambooHR / Workable subdomain, Recruitee company id — path segments only. */
	private String subdomain;
	private String companyId;

	/** Greenhouse Harvest requires On-Behalf-Of for note writes. */
	private String onBehalfOfUserId;

	private String accessToken;
	private String refreshToken;
	private Instant tokenExpiresAt;

	/** Zoho datacenter key (com, eu, in, com.au, jp, ca, sa, com.cn) from the OAuth grant. */
	private String datacenter;

	/**
	 * API host returned with the Zoho grant (api_domain, e.g. https://www.zohoapis.eu). It is
	 * authoritative for where this account's data lives — preferred over deriving a host from
	 * the datacenter key, which cannot know about regions Zoho adds later.
	 */
	private String apiDomain;
}
