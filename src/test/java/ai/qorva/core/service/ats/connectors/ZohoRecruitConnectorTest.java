package ai.qorva.core.service.ats.connectors;

import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.service.ats.AtsCredentials;
import ai.qorva.core.service.ats.AtsHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A Zoho account exists in one datacenter and its data is unreachable from any other, so
 * the API host has to follow the grant rather than a compiled-in default.
 */
@ExtendWith(MockitoExtension.class)
class ZohoRecruitConnectorTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock private AtsHttpClient http;

	private ZohoRecruitConnector connector;

	@BeforeEach
	void setUp() {
		connector = new ZohoRecruitConnector(http, objectMapper);
	}

	private String requestedUrl() throws Exception {
		var url = ArgumentCaptor.forClass(String.class);
		verify(http).getJson(eq(AtsProviderEnum.ZOHO_RECRUIT), url.capture(), anyMap());
		return url.getValue();
	}

	@Test
	void apiDomainFromTheGrantDecidesTheHost() throws Exception {
		when(http.getJson(eq(AtsProviderEnum.ZOHO_RECRUIT), anyString(), anyMap()))
			.thenReturn(objectMapper.readTree("{}"));

		connector.validate(AtsCredentials.builder()
			.accessToken("t").datacenter("com").apiDomain("https://www.zohoapis.eu").build());

		// api_domain wins even when the stored datacenter key says otherwise.
		assertThat(requestedUrl()).startsWith("https://www.zohoapis.eu/recruit/v2/");
	}

	@Test
	void aTrailingSlashOnApiDomainDoesNotDoubleUp() throws Exception {
		when(http.getJson(eq(AtsProviderEnum.ZOHO_RECRUIT), anyString(), anyMap()))
			.thenReturn(objectMapper.readTree("{}"));

		connector.validate(AtsCredentials.builder()
			.accessToken("t").apiDomain("https://www.zohoapis.in/").build());

		assertThat(requestedUrl()).startsWith("https://www.zohoapis.in/recruit/v2/");
	}

	@Test
	void grantsPredatingApiDomainFallBackToTheDatacenterHost() throws Exception {
		when(http.getJson(eq(AtsProviderEnum.ZOHO_RECRUIT), anyString(), anyMap()))
			.thenReturn(objectMapper.readTree("{}"));

		connector.validate(AtsCredentials.builder().accessToken("t").datacenter("com.au").build());

		assertThat(requestedUrl()).startsWith("https://recruit.zoho.com.au/recruit/v2/");
	}

	@Test
	void tokensAreSentAsZohoOauthtoken() throws Exception {
		when(http.getJson(eq(AtsProviderEnum.ZOHO_RECRUIT), anyString(), anyMap()))
			.thenReturn(objectMapper.readTree("{}"));

		connector.validate(AtsCredentials.builder().accessToken("the-token").build());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<java.util.Map<String, String>> headers = ArgumentCaptor.forClass(java.util.Map.class);
		verify(http).getJson(eq(AtsProviderEnum.ZOHO_RECRUIT), anyString(), headers.capture());
		assertThat(headers.getValue()).containsEntry("Authorization", "Zoho-oauthtoken the-token");
	}
}
