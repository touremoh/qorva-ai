package ai.qorva.core.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Contract snapshot of every read the web app makes that is served by MongoDB alone (no OpenAI,
 * S3, Stripe or Graph call). Each response — status and normalised body — must match its golden
 * file in {@code src/test/resources/contracts/read.*.json}; the refactor may not change any of them.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiReadContractIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TwoTenantFixture fixture;

	private TwoTenantFixture.SeededTenant a;
	private String owner;
	private String viewer;

	@BeforeAll
	void seed() {
		var seeded = fixture.reset();
		a = seeded.a();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
	}

	private record Route(String name, MockHttpServletRequestBuilder request, Map<String, String> rankedArrays) {
		Route(String name, MockHttpServletRequestBuilder request) {
			this(name, request, Map.of());
		}
	}

	/**
	 * The dashboard ranks by counts and leaves equal counts in database order; the skills list is a
	 * top 10, so which tied skills make the cut varies too (a deterministic tie-break is a follow-up).
	 */
	private static final Map<String, String> DASHBOARD_RANKINGS = Map.of(
		"skillsReport", "totalMatch,cutoff",
		"jobPostsReport", "totalMatch",
		"skillDepthReport", "count",
		"seniorityLevelReport", "count",
		"leadershipReport", "count",
		"learningVelocityReport", "count");

	@TestFactory
	Stream<DynamicTest> ownerReads() {
		return routes().stream().map(route -> DynamicTest.dynamicTest(route.name(), () -> {
			var response = mvc.perform(route.request().header("Authorization", owner)).andReturn().getResponse();
			ContractSnapshots.assertMatches("read." + route.name(), response.getStatus(), response.getContentAsString(),
				route.rankedArrays());
		}));
	}

	/** The same reads as a read-only teammate: pins which ones the permission model lets through. */
	@TestFactory
	Stream<DynamicTest> viewerReads() {
		return routes().stream().map(route -> DynamicTest.dynamicTest(route.name(), () -> {
			var response = mvc.perform(route.request().header("Authorization", viewer)).andReturn().getResponse();
			// Only the status: the bodies are already pinned by ownerReads.
			ContractSnapshots.assertMatches("viewer." + route.name(), response.getStatus(), null);
		}));
	}

	@TestFactory
	Stream<DynamicTest> publicReads() {
		return Stream.of(
			new Route("registrations.products", get("/registrations/products")),
			new Route("stripe.checkoutCancel", get("/stripe/checkout/cancel"))
		).map(route -> DynamicTest.dynamicTest(route.name(), () -> {
			var response = mvc.perform(route.request()).andReturn().getResponse();
			ContractSnapshots.assertMatches("public." + route.name(), response.getStatus(), response.getContentAsString());
		}));
	}

	private List<Route> routes() {
		return List.of(
			new Route("cvs.list", get("/cvs").param("pageNumber", "0").param("pageSize", "25")),
			new Route("cvs.list.quickSearchSorted", get("/cvs").param("pageNumber", "0").param("pageSize", "25")
				.param("q", "engineer").param("sort", "name,asc")),
			new Route("cvs.list.archived", get("/cvs").param("pageNumber", "0").param("pageSize", "25").param("archived", "true")),
			new Route("cvs.one", get("/cvs/" + a.cvId())),
			new Route("cvs.filterOptions", get("/cvs/filter-options")),
			new Route("cvs.duplicates", get("/cvs/duplicates")),
			new Route("cvs.search", get("/cvs/search").param("searchTerms", "engineer").param("pageSize", "8").param("pageNumber", "0")),
			new Route("cvs.bulkUploads", get("/cvs/bulk-uploads")),
			new Route("cvs.clearLibraryPreflight", get("/cvs/clear-library/preflight")),
			new Route("jobs.list", get("/jobs").param("pageNumber", "0").param("pageSize", "25")),
			new Route("jobs.one", get("/jobs/" + a.jobId())),
			new Route("reports.list", get("/matching-reports").param("pageNumber", "0").param("pageSize", "10")),
			new Route("reports.list.byJob", get("/matching-reports").param("pageNumber", "0").param("pageSize", "10")
				.param("jobPostId", a.jobId())),
			new Route("reports.search", get("/matching-reports/search").param("pageNumber", "0").param("pageSize", "10")
				.param("searchTerms", "a")),
			new Route("reports.findByCriteria", post("/matching-reports/search").contentType(MediaType.APPLICATION_JSON)
				.content("{\"jobPostId\":\"" + a.jobId() + "\",\"candidateInfo\":{\"candidateId\":\"" + a.cvId() + "\"}}")),
			new Route("reports.exportCsv", get("/matching-reports/export/csv").param("jobPostId", a.jobId()).param("format", "global")),
			new Route("notes.onCv", get("/notes").param("targetType", "CV").param("targetId", a.cvId())),
			new Route("chats.list", get("/chats").param("page", "0").param("size", "25")),
			new Route("chats.allowed", get("/chats/allowed")),
			new Route("chats.one", get("/chats/" + a.chatId())),
			new Route("chats.messages", get("/chats/" + a.chatId() + "/messages").param("page", "0").param("size", "50")),
			new Route("dashboard.data", get("/dashboard/data"), DASHBOARD_RANKINGS),
			new Route("dashboard.topCandidates", get("/dashboard/top-candidates").param("pageNumber", "0").param("pageSize", "5")),
			new Route("emailTemplates.list", get("/email-templates/candidate-update")),
			new Route("insights.conversations", get("/library-insights/conversations")),
			new Route("insights.conversation", get("/library-insights/conversations/" + a.conversationId())),
			new Route("quality.report", get("/library-quality")),
			new Route("quality.summary", get("/library-quality/summary")),
			new Route("quality.jobs", get("/library-quality/jobs")),
			new Route("quality.issues", get("/library-quality/issues").param("issueKey", "MISSING_PHONE")
				.param("pageNumber", "0").param("pageSize", "20")),
			new Route("mailbox.availability", get("/mailbox-connections/availability")),
			new Route("mailbox.me", get("/mailbox-connections/me")),
			new Route("usage.current", get("/usage-monitoring/current")),
			new Route("users.list", get("/users")),
			new Route("users.mfa", get("/users/me/mfa")),
			new Route("tenants.own", get("/tenants/" + a.tenantId())),
			new Route("tenants.logo", get("/tenants/logo")),
			new Route("ats.providers", get("/ats/providers")),
			new Route("ats.connections", get("/ats/connections")),
			new Route("outreach.context", get("/candidate-outreach/context").param("cvId", a.cvId()))
		);
	}
}
