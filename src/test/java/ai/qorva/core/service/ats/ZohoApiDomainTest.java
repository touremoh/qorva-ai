package ai.qorva.core.service.ats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZohoApiDomainTest {

	@Test
	void onlyZohosOwnApiHostsAreKept() {
		assertThat(AtsOauthService.knownZohoApiDomain("https://www.zohoapis.eu/")).isEqualTo("https://www.zohoapis.eu");
		assertThat(AtsOauthService.knownZohoApiDomain("https://attacker.example")).isNull();
		assertThat(AtsOauthService.knownZohoApiDomain("http://www.zohoapis.com")).isNull();
		assertThat(AtsOauthService.knownZohoApiDomain(null)).isNull();
	}
}
