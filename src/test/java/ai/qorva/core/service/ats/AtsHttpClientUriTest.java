package ai.qorva.core.service.ats;

import ai.qorva.core.enums.AtsProviderEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AtsHttpClientUriTest {

	/**
	 * A Recruitee resume link. The %2F separators inside X-Amz-Credential are the part that
	 * matters: re-escaping them to %252F is what made S3 answer every download with
	 * 400 AuthorizationQueryParametersError.
	 */
	private static final String PRESIGNED_URL =
		"https://recruitee-main.s3.eu-central-1.amazonaws.com/candidates/130654853/cv_rr9estw1u75h.pdf"
			+ "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
			+ "&X-Amz-Credential=AKIAEXAMPLE%2F20260911%2Feu-central-1%2Fs3%2Faws4_request"
			+ "&X-Amz-Signature=8f2b1c";

	private MockRestServiceServer server;
	private AtsHttpClient client;

	@BeforeEach
	void setUp() {
		var builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.client = new AtsHttpClient(builder, new ObjectMapper());
	}

	@Test
	void aPreSignedResumeLinkIsRequestedExactlyAsTheProviderGaveIt() throws Exception {
		server.expect(requestTo(URI.create(PRESIGNED_URL)))
			.andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_PDF));

		var bytes = client.getBytes(AtsProviderEnum.RECRUITEE, PRESIGNED_URL, Map.of());

		assertThat(bytes).containsExactly(1, 2, 3);
		server.verify();
	}

	@Test
	void anAlreadyEscapedQueryValueSurvivesAJsonCall() throws Exception {
		var url = "https://api.recruitee.com/c/12345/candidates?limit=100&filter=a%2Fb";
		server.expect(requestTo(URI.create(url)))
			.andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

		var body = client.getJson(AtsProviderEnum.RECRUITEE, url, Map.of());

		assertThat(body.path("candidates")).isEmpty();
		server.verify();
	}
}
