package ai.qorva.core.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Not part of the regular suite: exports the real responses of tenant A's screens as the API fixture
 * the web app's Playwright tests serve (so the browser tests run against what the backend actually
 * returns, not hand-written mocks). Regenerate after an intended API change:
 *
 * <pre>./mvnw test -Dtest=AppFixtureExportIntegrationTest -Dapp.fixtures=../qorva-ai-app/e2e/fixtures/api.json</pre>
 */
@EnabledIfSystemProperty(named = "app.fixtures", matches = ".+")
class AppFixtureExportIntegrationTest extends AbstractIntegrationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	@Autowired
	private TwoTenantFixture fixture;

	@Test
	void exportTenantAScreens() throws Exception {
		var a = fixture.reset().a();
		var owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		var out = JsonNodeFactory.instance.objectNode();

		var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + a.ownerEmail() + "\",\"rawPassword\":\"" + TwoTenantFixture.PASSWORD + "\"}"))
			.andReturn().getResponse();
		out.set("POST /auth/login", entry(login.getStatus(), login.getContentAsString()));

		var requests = new ArrayList<MockHttpServletRequestBuilder>(List.of(
			get("/cvs"), get("/cvs/filter-options"), get("/cvs/duplicates"), get("/cvs/bulk-uploads"),
			get("/cvs/clear-library/preflight"), get("/cvs/search").param("searchTerms", "engineer").param("pageSize", "8").param("pageNumber", "0"),
			get("/jobs").param("pageNumber", "0").param("pageSize", "25"),
			get("/matching-reports").param("pageNumber", "0").param("pageSize", "10"),
			get("/chats").param("page", "0").param("size", "25"), get("/chats/allowed"),
			get("/dashboard/data"), get("/dashboard/top-candidates").param("pageNumber", "0").param("pageSize", "5"),
			get("/email-templates/candidate-update"), get("/library-insights/conversations"),
			get("/library-quality"), get("/library-quality/summary"), get("/library-quality/jobs"),
			get("/mailbox-connections/availability"), get("/usage-monitoring/current"), get("/users"), get("/users/me/mfa"),
			get("/tenants/" + a.tenantId()), get("/ats/providers"), get("/ats/connections"),
			get("/registrations/products"), get("/notes").param("targetType", "CV").param("targetId", a.cvId()),
			get("/candidate-outreach/context").param("cvId", a.cvId()),
			get("/library-insights/conversations/" + a.conversationId()),
			get("/chats/" + a.chatId()), get("/chats/" + a.chatId() + "/messages").param("page", "0").param("size", "50")));
		a.cvIds().forEach(id -> requests.add(get("/cvs/" + id)));
		a.jobIds().forEach(id -> requests.add(get("/jobs/" + id)));

		for (var request : requests) {
			var built = request.buildRequest(null);
			var key = built.getMethod() + " " + built.getRequestURI();
			var response = mvc.perform(request.header("Authorization", owner)).andReturn().getResponse();
			out.set(key, entry(response.getStatus(), response.getContentAsString()));
		}

		var target = Path.of(System.getProperty("app.fixtures"));
		Files.createDirectories(target.getParent());
		Files.writeString(target, MAPPER.writeValueAsString(out) + "\n");
	}

	private static ObjectNode entry(int status, String body) {
		var entry = JsonNodeFactory.instance.objectNode();
		entry.put("status", status);
		entry.set("body", body.isBlank() ? JsonNodeFactory.instance.nullNode() : parse(body));
		return entry;
	}

	private static JsonNode parse(String body) {
		try {
			return MAPPER.readTree(body);
		} catch (Exception notJson) {
			return JsonNodeFactory.instance.textNode(body);
		}
	}
}
