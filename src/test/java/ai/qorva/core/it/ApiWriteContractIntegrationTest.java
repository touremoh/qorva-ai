package ai.qorva.core.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Contract snapshot of every write the web app makes that MongoDB alone serves: the response of
 * the write and the state it leaves behind (including cascades), each pinned in
 * {@code src/test/resources/contracts/write.*.json}. Every test starts from a freshly seeded pair
 * of tenants.
 */
class ApiWriteContractIntegrationTest extends AbstractIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@Autowired
	private TwoTenantFixture fixture;

	private TwoTenantFixture.SeededTenant a;
	private String owner;
	private String viewer;

	@BeforeEach
	void seed() {
		a = fixture.reset().a();
		owner = fixture.bearer(a.ownerEmail(), a.tenantId());
		viewer = fixture.bearer(a.viewerEmail(), a.tenantId());
	}

	private void snap(String name, String token, MockHttpServletRequestBuilder request) throws Exception {
		if (token != null) {
			request.header("Authorization", token);
		}
		var response = mvc.perform(request).andReturn().getResponse();
		ContractSnapshots.assertMatches("write." + name, response.getStatus(), response.getContentAsString());
	}

	@Test
	void cvPatch_updatesTheCv_andFlagsOpenJobsForRescreening() throws Exception {
		snap("cvPatch.response", owner, patch("/cvs/" + a.cvId()).contentType(JSON)
			.content("{\"tags\":[\"shortlist\",\"java\"],\"candidateProfileSummary\":\"Updated summary.\"}"));
		snap("cvPatch.cvAfter", owner, get("/cvs/" + a.cvId()));
		snap("cvPatch.jobsAfter", owner, get("/jobs").param("pageNumber", "0").param("pageSize", "25"));
	}

	@Test
	void cvDelete_cascadesToReportsNotesAndChats() throws Exception {
		snap("cvDelete.response", owner, delete("/cvs/" + a.cvId()));
		snap("cvDelete.cvAfter", owner, get("/cvs/" + a.cvId()));
		snap("cvDelete.reportsAfter", owner, get("/matching-reports").param("pageNumber", "0").param("pageSize", "10"));
		snap("cvDelete.notesAfter", owner, get("/notes").param("targetType", "CV").param("targetId", a.cvId()));
		snap("cvDelete.chatsAfter", owner, get("/chats").param("page", "0").param("size", "25"));
		snap("cvDelete.dashboardCounts", owner, get("/cvs/clear-library/preflight"));
	}

	@Test
	void cvDelete_needsDeleteCv() throws Exception {
		snap("cvDelete.asViewer", viewer, delete("/cvs/" + a.cvId()));
	}

	@Test
	void jobLifecycle_createUpdateCloseDelete() throws Exception {
		snap("jobCreate.response", owner, post("/jobs").contentType(JSON).content("""
			{"title":"Staff Platform Engineer","description":"<p>Own our Kubernetes platform.</p>","status":"open"}
			"""));
		snap("jobPut.response", owner, put("/jobs/" + a.jobId()).contentType(JSON).content("""
			{"title":"Senior Backend Engineer (Java)","description":"<p>Updated.</p>","status":"open"}
			"""));
		snap("jobClose.response", owner, patch("/jobs/" + a.jobId()).contentType(JSON).content("{\"status\":\"closed\"}"));
		snap("jobClose.jobAfter", owner, get("/jobs/" + a.jobId()));
		snap("jobDelete.response", owner, delete("/jobs/" + a.jobId()));
		snap("jobDelete.reportsAfter", owner, get("/matching-reports").param("pageNumber", "0").param("pageSize", "10"));
		snap("jobDelete.chatsAfter", owner, get("/chats").param("page", "0").param("size", "25"));
	}

	@Test
	void reportDelete() throws Exception {
		snap("reportDelete.response", owner, delete("/matching-reports/" + a.reportId()));
		snap("reportDelete.reportsAfter", owner, get("/matching-reports").param("pageNumber", "0").param("pageSize", "10"));
		snap("reportDelete.chatsAfter", owner, get("/chats").param("page", "0").param("size", "25"));
	}

	@Test
	void notesLifecycle() throws Exception {
		var body = "{\"targetType\":\"CV\",\"targetId\":\"" + a.cvId() + "\",\"text\":\"%s\"}";
		snap("noteCreate.response", owner, post("/notes").contentType(JSON).content(body.formatted("Call back on Monday.")));
		snap("noteUpdate.response", owner, put("/notes/" + a.noteId()).contentType(JSON).content(body.formatted("Edited note.")));
		snap("noteDelete.response", owner, delete("/notes/" + a.noteId()));
		snap("notes.after", owner, get("/notes").param("targetType", "CV").param("targetId", a.cvId()));
		snap("noteCreate.asViewer", viewer, post("/notes").contentType(JSON).content(body.formatted("Viewer note.")));
	}

	@Test
	void emailTemplatesLifecycle() throws Exception {
		var body = "{\"name\":\"%s\",\"subject\":\"Update, {{candidate_name}}?\",\"bodyText\":\"Hi {{candidate_name}}, please update your CV at {{company_name}}.\"}";
		snap("templateCreate.response", owner, post("/email-templates/candidate-update").contentType(JSON)
			.content(body.formatted("Second template")));
		snap("templateUpdate.response", owner, put("/email-templates/candidate-update/" + a.templateId()).contentType(JSON)
			.content(body.formatted("Renamed template")));
		snap("templatePreview.response", owner, post("/email-templates/candidate-update/preview").contentType(JSON)
			.content("{\"subject\":\"Hello {{candidate_name}}\",\"bodyText\":\"Line <b>one</b>\\nLine two\"}"));
		snap("templateDelete.response", owner, delete("/email-templates/candidate-update/" + a.templateId()));
		snap("templates.after", owner, get("/email-templates/candidate-update"));
	}

	@Test
	void chatAndConversationLifecycle() throws Exception {
		snap("chatClose.response", owner, patch("/chats/" + a.chatId() + "/status").param("status", "CLOSED"));
		snap("chatDelete.response", owner, delete("/chats/" + a.chatId()));
		snap("chats.after", owner, get("/chats").param("page", "0").param("size", "25"));
		snap("conversationDelete.response", owner, delete("/library-insights/conversations/" + a.conversationId()));
		snap("conversations.after", owner, get("/library-insights/conversations"));
	}

	@Test
	void usersLifecycle() throws Exception {
		snap("userProfile.response", owner, patch("/users/" + a.ownerId()).contentType(JSON)
			.content("{\"firstName\":\"Olive\",\"lastName\":\"Owner\"}"));
		snap("userProfile.viewerEditsOwner", viewer, patch("/users/" + a.ownerId()).contentType(JSON)
			.content("{\"firstName\":\"Nope\"}"));
		snap("userAuthorities.response", owner, put("/users/" + a.viewerId() + "/authorities").contentType(JSON)
			.content("{\"authorities\":[{\"role\":\"ACCOUNT_OWNER\",\"action\":\"VIEW_CV\",\"permission\":\"ALLOWED\"}]}"));
		snap("userPassword.wrongCurrent", owner, patch("/users/" + a.ownerId() + "/password").contentType(JSON)
			.content("{\"currentPassword\":\"not-it\",\"newPassword\":\"Another-Pass-42\"}"));
		snap("userDelete.asViewer", viewer, delete("/users/" + a.ownerId()));
		snap("userDelete.response", owner, delete("/users/" + a.viewerId()));
		snap("users.after", owner, get("/users"));
	}

	@Test
	void authentication() throws Exception {
		snap("login.ok", null, post("/auth/login").contentType(JSON)
			.content("{\"email\":\"" + a.ownerEmail() + "\",\"rawPassword\":\"" + TwoTenantFixture.PASSWORD + "\"}"));
		snap("login.wrongPassword", null, post("/auth/login").contentType(JSON)
			.content("{\"email\":\"" + a.ownerEmail() + "\",\"rawPassword\":\"wrong\"}"));
		snap("login.unknownUser", null, post("/auth/login").contentType(JSON)
			.content("{\"email\":\"nobody@qorva.test\",\"rawPassword\":\"wrong\"}"));
		snap("token.validate", owner, post("/auth/token/validate"));
		snap("token.refresh", owner, post("/auth/token/refresh"));
		snap("noToken.cvs", null, get("/cvs"));
		snap("garbageToken.cvs", "Bearer not.a.jwt", get("/cvs"));
	}

	@Test
	void libraryQualityDismissAndReopen() throws Exception {
		snap("qualityDismiss.response", owner, post("/library-quality/issues/MISSING_PHONE/dismiss"));
		snap("qualityDismiss.reportAfter", owner, get("/library-quality/summary"));
		snap("qualityReopen.response", owner, post("/library-quality/issues/MISSING_PHONE/reopen"));
		snap("qualityReopen.reportAfter", owner, get("/library-quality/summary"));
	}

	@Test
	void outreachExternalHandOffIsLogged() throws Exception {
		snap("outreachExternal.response", owner, post("/candidate-outreach/external").contentType(JSON).content(
			"{\"cvId\":\"" + a.cvId() + "\",\"via\":\"GMAIL\",\"to\":\"candidate@example.com\",\"subject\":\"Hello\",\"body\":\"Hi there\"}"));
		snap("outreachExternal.contextAfter", owner, get("/candidate-outreach/context").param("cvId", a.cvId()));
	}
}
