package ai.qorva.core.service.sso;

import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MicrosoftSsoServiceTest {

	private static final String CLIENT = "client-1";
	private static final String TID = "tenant-1";

	private static Map<String, Object> claims() {
		var claims = new HashMap<String, Object>();
		claims.put("aud", List.of(CLIENT));
		claims.put("nonce", "n1");
		claims.put("tid", TID);
		claims.put("iss", "https://login.microsoftonline.com/" + TID + "/v2.0");
		claims.put("preferred_username", "ana@acme.com");
		return claims;
	}

	private static String messageOf(Map<String, Object> claims) {
		try {
			return MicrosoftSsoService.identityOf(claims, CLIENT, "n1");
		} catch (QorvaException e) {
			return e.getMessage();
		}
	}

	@Test
	void theWorkAccountUsernameIsTheIdentity() throws QorvaException {
		assertThat(MicrosoftSsoService.identityOf(claims(), CLIENT, "n1")).isEqualTo("ana@acme.com");
		var single = claims();
		single.put("aud", CLIENT);
		assertThat(MicrosoftSsoService.identityOf(single, CLIENT, "n1")).isEqualTo("ana@acme.com");
	}

	@Test
	void anotherAudienceNonceOrIssuerIsRefused() {
		var aud = claims(); aud.put("aud", List.of("other-app"));
		var nonce = claims(); nonce.put("nonce", "n2");
		var iss = claims(); iss.put("iss", "https://login.microsoftonline.com/another-tenant/v2.0");
		assertThat(List.of(messageOf(aud), messageOf(nonce), messageOf(iss))).containsOnly("error.auth.sso_failed");
	}

	@Test
	void anUnverifiedEmailClaimIsNeverTrusted() throws QorvaException {
		var unverified = claims();
		unverified.remove("preferred_username");
		unverified.put("email", "ceo@victim.com");
		assertThat(messageOf(unverified)).isEqualTo("error.auth.sso_no_account");

		unverified.put("xms_edov", true);
		assertThat(MicrosoftSsoService.identityOf(unverified, CLIENT, "n1")).isEqualTo("ceo@victim.com");
	}

	@Test
	void thePkceChallengeIsTheS256OfTheVerifier() {
		// RFC 7636 appendix B.
		assertThat(MicrosoftSsoService.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
			.isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
	}
}
