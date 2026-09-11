package ai.qorva.core.service.ats.connectors;

import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ats.AtsCredentials;
import ai.qorva.core.service.ats.AtsHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruiteeConnectorTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock private AtsHttpClient http;

	private RecruiteeConnector connector;

	@BeforeEach
	void setUp() {
		connector = new RecruiteeConnector(http, objectMapper);
	}

	private static AtsCredentials credentials() {
		return AtsCredentials.builder().apiKey("rc-token").companyId("12345").build();
	}

	private com.fasterxml.jackson.databind.JsonNode json(String raw) throws Exception {
		return objectMapper.readTree(raw);
	}

	/** The index entry carries no ad body — the one field that made imported jobs description-less. */
	private static final String ABRIDGED_INDEX = """
		{"offers":[{"id":2744061,"title":"Senior Marketer","status":"published",
		"careers_url":"https://acme.recruitee.com/o/senior-marketer","updated_at":"2026-09-11T20:54:30Z"}]}
		""";

	@Test
	void anOfferWithoutAnAdBodyInTheIndexIsCompletedFromTheSingleOfferRead() throws Exception {
		when(http.getJson(eq(AtsProviderEnum.RECRUITEE), endsWith("/offers"), anyMap()))
			.thenReturn(json(ABRIDGED_INDEX));
		when(http.getJson(eq(AtsProviderEnum.RECRUITEE), contains("/offers/2744061"), anyMap()))
			.thenReturn(json("""
				{"offer":{"id":2744061,"title":"Senior Marketer",
				"description":"<p>Own the funnel.</p>","requirements":"<p>5 years B2B.</p>"}}
				"""));

		var page = connector.listJobs(credentials(), null);

		assertThat(page.items()).singleElement().satisfies(job -> {
			assertThat(job.externalId()).isEqualTo("2744061");
			assertThat(job.title()).isEqualTo("Senior Marketer");
			// Both halves of the ad, because screening matches against the requirements too.
			assertThat(job.description()).isEqualTo("<p>Own the funnel.</p>\n\n<p>5 years B2B.</p>");
			assertThat(job.open()).isTrue();
		});
	}

	@Test
	void anIndexThatAlreadyCarriesTheAdBodyCostsNoExtraCall() throws Exception {
		when(http.getJson(eq(AtsProviderEnum.RECRUITEE), endsWith("/offers"), anyMap()))
			.thenReturn(json("""
				{"offers":[{"id":7,"title":"Data Engineer","status":"published",
				"description":"<p>Pipelines.</p>","requirements":"<p>Spark.</p>"}]}
				"""));

		var page = connector.listJobs(credentials(), null);

		assertThat(page.items()).singleElement()
			.extracting(job -> job.description())
			.isEqualTo("<p>Pipelines.</p>\n\n<p>Spark.</p>");
		verify(http, never()).getJson(eq(AtsProviderEnum.RECRUITEE), contains("/offers/7"), anyMap());
	}

	@Test
	void anOfferWithOnlyRequirementsStillImportsThem() throws Exception {
		when(http.getJson(eq(AtsProviderEnum.RECRUITEE), endsWith("/offers"), anyMap()))
			.thenReturn(json(ABRIDGED_INDEX));
		when(http.getJson(eq(AtsProviderEnum.RECRUITEE), contains("/offers/2744061"), anyMap()))
			.thenReturn(json("""
				{"offer":{"id":2744061,"description":null,"requirements":"<p>5 years B2B.</p>"}}
				"""));

		assertThat(connector.listJobs(credentials(), null).items())
			.singleElement()
			.extracting(job -> job.description())
			.isEqualTo("<p>5 years B2B.</p>");
	}

	/** A detail read that fails must not take the whole job sync down with it. */
	@Test
	void aFailedDetailReadStillImportsTheJob() throws Exception {
		when(http.getJson(eq(AtsProviderEnum.RECRUITEE), endsWith("/offers"), anyMap()))
			.thenReturn(json(ABRIDGED_INDEX));
		when(http.getJson(eq(AtsProviderEnum.RECRUITEE), contains("/offers/2744061"), anyMap()))
			.thenThrow(new QorvaException("error.ats.api_error"));

		assertThat(connector.listJobs(credentials(), null).items())
			.singleElement()
			.satisfies(job -> {
				assertThat(job.title()).isEqualTo("Senior Marketer");
				assertThat(job.description()).isNull();
			});
	}
}
