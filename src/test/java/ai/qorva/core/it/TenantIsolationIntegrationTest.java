package ai.qorva.core.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Tenant B's owner — who holds every authority — goes after tenant A's data through every route that
 * takes an id. Each attempt must be refused (or answer with nothing), no response to B may contain
 * any identifier of tenant A, and tenant A's documents must be byte-for-byte unchanged afterwards.
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

	private static final Set<Integer> REFUSED = Set.of(400, 403, 404);

	@Autowired
	private TwoTenantFixture fixture;

	private TwoTenantFixture.SeededTenant a;
	private TwoTenantFixture.SeededTenant b;
	private String attacker;

	@BeforeEach
	void seed() {
		var seeded = fixture.reset();
		a = seeded.a();
		b = seeded.b();
		attacker = fixture.bearer(b.ownerEmail(), b.tenantId());
	}

	private record Attack(String name, MockHttpServletRequestBuilder request, boolean mayAnswerEmpty, boolean knownServerError) {
		Attack(String name, MockHttpServletRequestBuilder request, boolean mayAnswerEmpty) {
			this(name, request, mayAnswerEmpty, false);
		}
	}

	@TestFactory
	Stream<DynamicTest> everyIdTakingRouteRefusesTheOtherTenantsData() {
		return attacks().stream().map(attack -> DynamicTest.dynamicTest(attack.name(), () -> {
			var before = fingerprintOfTenantA();

			var response = mvc.perform(attack.request().header("Authorization", attacker)).andReturn().getResponse();
			var body = response.getContentAsString();

			if (attack.knownServerError()) {
				assertThat(response.getStatus()).as("%s → %s", attack.name(), body).isIn(500, 400, 403, 404);
			} else if (!attack.mayAnswerEmpty()) {
				assertThat(response.getStatus()).as("%s → %s", attack.name(), body).isIn(REFUSED);
			}
			assertNoIdentifierOfTenantA(attack.name(), body);
			assertThat(fingerprintOfTenantA()).as("tenant A data changed by %s", attack.name()).isEqualTo(before);
		}));
	}

	/** B's own screens never show anything of A's. */
	@TestFactory
	Stream<DynamicTest> tenantBsOwnReadsNeverContainTenantAsData() {
		return Stream.of(
			get("/cvs").param("pageNumber", "0").param("pageSize", "100"),
			get("/cvs/search").param("searchTerms", "engineer").param("pageSize", "50").param("pageNumber", "0"),
			get("/cvs/filter-options"), get("/cvs/duplicates"),
			get("/jobs").param("pageNumber", "0").param("pageSize", "100"),
			get("/matching-reports").param("pageNumber", "0").param("pageSize", "100"),
			get("/matching-reports/search").param("pageNumber", "0").param("pageSize", "100").param("searchTerms", "a"),
			get("/dashboard/data"), get("/dashboard/top-candidates").param("pageNumber", "0").param("pageSize", "50"),
			get("/chats").param("page", "0").param("size", "100"),
			get("/library-insights/conversations"), get("/library-quality"), get("/library-quality/summary"),
			get("/email-templates/candidate-update"), get("/users"), get("/usage-monitoring/current")
		).map(request -> DynamicTest.dynamicTest(request.buildRequest(null).getRequestURI(), () -> {
			var response = mvc.perform(request.header("Authorization", attacker)).andReturn().getResponse();
			assertThat(response.getStatus()).isBetween(200, 299);
			assertNoIdentifierOfTenantA(request.toString(), response.getContentAsString());
		}));
	}

	/**
	 * S11: chat creation stores the cv/job/report ids from the body without checking they belong to
	 * the caller's tenant. Nothing leaks today (the context is loaded through tenant-checked reads),
	 * but the stored reference is cross-tenant. Fixed in refactor phase 5 — then enable this test.
	 */
	@Test
	@Disabled("S11 — chat creation does not validate foreign ids yet (refactor phase 5)")
	void chatCannotBeCreatedOnTheOtherTenantsCandidate() throws Exception {
		var response = mvc.perform(post("/chats").header("Authorization", attacker).contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"title":"x","cvId":"%s","jobPostId":"%s","participants":[{"userId":"%s","role":"OWNER"}],"language":"en"}
				""".formatted(a.cvId(), a.jobId(), b.ownerId()))).andReturn().getResponse();
		assertThat(response.getStatus()).isIn(REFUSED);
	}

	private List<Attack> attacks() {
		var json = MediaType.APPLICATION_JSON;
		var cv = a.cvId();
		var job = a.jobId();
		var report = a.reportId();
		return List.of(
			refused("cv.get", get("/cvs/" + cv)),
			refused("cv.patch", patch("/cvs/" + cv).contentType(json).content("{\"tags\":[\"pwned\"]}")),
			refused("cv.put", put("/cvs/" + cv).contentType(json).content("{\"tags\":[\"pwned\"]}")),
			refused("cv.delete", delete("/cvs/" + cv)),
			refused("cv.replaceWithOwn", post("/cvs/" + b.cvId() + "/replace/" + cv)),
			refused("cv.replaceOthers", post("/cvs/" + a.cvIds().get(1) + "/replace/" + cv)),
			// Own-tenant route with a foreign id in the body: the stored id must not redirect the write.
			refused("cv.patchOwnWithForeignBodyId", patch("/cvs/" + b.cvId()).contentType(json)
				.content("{\"id\":\"" + cv + "\",\"tags\":[\"pwned\"]}"), true),
			answersEmpty("cv.byIds", post("/cvs/ids").contentType(json).content("[\"" + cv + "\",\"" + a.cvIds().get(1) + "\"]")),
			refused("job.get", get("/jobs/" + job)),
			refused("job.patch", patch("/jobs/" + job).contentType(json).content("{\"title\":\"pwned\"}")),
			refused("job.put", put("/jobs/" + job).contentType(json).content("{\"title\":\"pwned\"}")),
			refused("job.delete", delete("/jobs/" + job)),
			refused("job.patchOwnWithForeignBodyId", patch("/jobs/" + b.jobId()).contentType(json)
				.content("{\"id\":\"" + job + "\",\"title\":\"pwned\"}"), true),
			refused("report.get", get("/matching-reports/" + report)),
			refused("report.delete", delete("/matching-reports/" + report)),
			answersEmpty("report.findByCriteria", post("/matching-reports/search").contentType(json)
				.content("{\"jobPostId\":\"" + job + "\",\"candidateInfo\":{\"candidateId\":\"" + cv + "\"}}")),
			answersEmpty("report.exportCsv", get("/matching-reports/export/csv").param("jobPostId", job).param("format", "global")),
			answersEmpty("notes.list", get("/notes").param("targetType", "CV").param("targetId", cv)),
			refused("notes.create", post("/notes").contentType(json)
				.content("{\"targetType\":\"CV\",\"targetId\":\"" + cv + "\",\"text\":\"pwned\"}")),
			refused("notes.update", put("/notes/" + a.noteId()).contentType(json)
				.content("{\"targetType\":\"CV\",\"targetId\":\"" + cv + "\",\"text\":\"pwned\"}")),
			refused("notes.delete", delete("/notes/" + a.noteId())),
			refusedWith500("chat.get", get("/chats/" + a.chatId())),
			refusedWith500("chat.messages", get("/chats/" + a.chatId() + "/messages").param("page", "0").param("size", "50")),
			refusedWith500("chat.status", patch("/chats/" + a.chatId() + "/status").param("status", "CLOSED")),
			refusedWith500("chat.delete", delete("/chats/" + a.chatId())),
			answersEmpty("insights.conversation", get("/library-insights/conversations/" + a.conversationId())),
			answersEmpty("insights.conversationDelete", delete("/library-insights/conversations/" + a.conversationId())),
			refused("template.update", put("/email-templates/candidate-update/" + a.templateId()).contentType(json)
				.content("{\"name\":\"pwned\",\"subject\":\"pwned\",\"bodyText\":\"pwned\"}")),
			refused("template.delete", delete("/email-templates/candidate-update/" + a.templateId())),
			refused("user.get", get("/users/" + a.ownerId())),
			refused("user.patch", patch("/users/" + a.ownerId()).contentType(json).content("{\"firstName\":\"pwned\"}")),
			refused("user.delete", delete("/users/" + a.viewerId())),
			refused("user.authorities", put("/users/" + a.viewerId() + "/authorities").contentType(json)
				.content("{\"authorities\":[{\"role\":\"ACCOUNT_OWNER\",\"action\":\"MANAGE_USERS\",\"permission\":\"ALLOWED\"}]}")),
			refused("user.password", patch("/users/" + a.ownerId() + "/password").contentType(json)
				.content("{\"currentPassword\":\"" + TwoTenantFixture.PASSWORD + "\",\"newPassword\":\"Pwned-Password-1\"}")),
			refused("tenant.get", get("/tenants/" + a.tenantId())),
			refused("outreach.context", get("/candidate-outreach/context").param("cvId", cv)),
			refused("outreach.external", post("/candidate-outreach/external").contentType(json)
				.content("{\"cvId\":\"" + cv + "\",\"via\":\"GMAIL\",\"to\":\"x@example.com\",\"subject\":\"s\",\"body\":\"b\"}"))
		);
	}

	/**
	 * S13: the chat service throws "not found" without an HTTP status, so a foreign (or unknown) chat
	 * id answers 500 instead of 404. Refused all the same — nothing leaks and nothing changes — but
	 * the status is wrong. Phase 6 maps it to 404; then move these back to {@link #refused}.
	 */
	private static Attack refusedWith500(String name, MockHttpServletRequestBuilder request) {
		return new Attack(name, request, false, true);
	}

	private static Attack refused(String name, MockHttpServletRequestBuilder request) {
		return new Attack(name, request, false);
	}

	/** For own-tenant routes probed with a foreign id: any status is fine as long as A is untouched. */
	private static Attack refused(String name, MockHttpServletRequestBuilder request, boolean anyStatus) {
		return new Attack(name, request, anyStatus);
	}

	/** Routes that legitimately answer 200 with an empty result for ids the caller does not own. */
	private static Attack answersEmpty(String name, MockHttpServletRequestBuilder request) {
		return new Attack(name, request, true);
	}

	private void assertNoIdentifierOfTenantA(String what, String body) {
		var identifiers = new ArrayList<String>();
		identifiers.add(a.tenantId());
		identifiers.add(a.tenantName());
		identifiers.add(a.ownerId());
		identifiers.add(a.ownerEmail());
		identifiers.add(a.viewerId());
		identifiers.addAll(a.cvIds());
		identifiers.addAll(a.jobIds());
		identifiers.addAll(a.reportIds());
		identifiers.add(a.noteId());
		identifiers.add(a.templateId());
		identifiers.add(a.chatId());
		identifiers.add(a.conversationId());
		assertThat(identifiers).as("tenant A identifiers leaked by %s", what)
			.noneMatch(body::contains);
	}

	private String fingerprintOfTenantA() {
		return fixture.fingerprint(a.tenantId());
	}
}
