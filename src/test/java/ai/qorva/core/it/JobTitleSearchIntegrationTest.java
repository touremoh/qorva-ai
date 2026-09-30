package ai.qorva.core.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Job search matches the typed text literally: parentheses, dots and plus signs are not regex syntax. */
class JobTitleSearchIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TwoTenantFixture fixture;
	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void aTitleWithParenthesesIsFoundByItsOwnTitle() throws Exception {
		var a = fixture.reset().a();
		var owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		mvc.perform(post("/jobs").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Senior Backend Engineer (Java/Spring)\",\"description\":\"<p>Java 21.</p>\",\"status\":\"open\"}"));

		var body = mvc.perform(get("/jobs").header("Authorization", owner)
				.param("title", "Senior Backend Engineer (Java/Spring)").param("pageNumber", "0").param("pageSize", "25"))
			.andReturn().getResponse().getContentAsString();

		var titles = objectMapper.readTree(body).findValuesAsText("title");
		// Before the fix the "(...)" was a regex group, so the literal title never matched: nothing was found.
		assertThat(titles).isNotEmpty().containsOnly("Senior Backend Engineer (Java/Spring)");
	}

	@Test
	void regexCharactersInASearchAreLiteral() throws Exception {
		var a = fixture.reset().a();
		var owner = fixture.bearer(a.ownerEmail(), a.tenantId());

		var response = mvc.perform(get("/jobs").header("Authorization", owner)
				.param("title", "C++ (.NET").param("pageNumber", "0").param("pageSize", "25"))
			.andReturn().getResponse();

		// Before: an unbalanced "(" made the query itself invalid.
		assertThat(response.getStatus()).isEqualTo(200);
	}
}
