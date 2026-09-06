package ai.qorva.core.service.ats.connectors;

import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ats.AtsCredentials;
import ai.qorva.core.service.ats.AtsHttpClient;
import ai.qorva.core.service.ats.AtsModels;
import ai.qorva.core.service.ats.AtsWebhookVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AshbyConnectorTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock private AtsHttpClient http;

	private AshbyConnector connector;
	private final AtsCredentials credentials = AtsCredentials.builder().apiKey("ashby-key").build();

	@BeforeEach
	void setUp() {
		connector = new AshbyConnector(http, objectMapper);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object captured) {
		return (Map<String, Object>) captured;
	}

	@Test
	void candidatesMapWithEmailApplicationAndResumeHandle() throws Exception {
		var payload = """
			{
			  "results": [{
			    "id": "c-1",
			    "name": "Ada Lovelace",
			    "updatedAt": "2026-09-05T10:15:30Z",
			    "applicationIds": ["app-9"],
			    "primaryEmailAddress": {"value": "ada@example.com"},
			    "profileUrl": "https://app.ashbyhq.com/candidates/c-1",
			    "fileHandles": [
			      {"handle": "h-cover", "name": "cover.pdf", "type": "Other"},
			      {"handle": "h-resume", "name": "ada_cv.pdf", "type": "Resume"}
			    ]
			  }],
			  "moreDataAvailable": false
			}""";
		when(http.postJson(eq(AtsProviderEnum.ASHBY), contains("/candidate.list"), anyMap(), any()))
			.thenReturn(objectMapper.readTree(payload));

		var page = connector.listCandidates(credentials, null);

		assertThat(page.items()).hasSize(1);
		var candidate = page.items().get(0);
		assertThat(candidate.externalId()).isEqualTo("c-1");
		assertThat(candidate.name()).isEqualTo("Ada Lovelace");
		assertThat(candidate.email()).isEqualTo("ada@example.com");
		assertThat(candidate.externalApplicationId()).isEqualTo("app-9");
		assertThat(candidate.resumeHandle()).isEqualTo("h-resume");
		assertThat(candidate.resumeFilename()).isEqualTo("ada_cv.pdf");
		// moreDataAvailable false ends the stream regardless of any cursor echoed back.
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void theApiKeyIsSentAsBasicAuthOnAPostRpcEndpoint() throws Exception {
		when(http.postJson(eq(AtsProviderEnum.ASHBY), anyString(), anyMap(), any()))
			.thenReturn(objectMapper.readTree("{}"));

		connector.validate(credentials);

		var headers = ArgumentCaptor.forClass(Map.class);
		var url = ArgumentCaptor.forClass(String.class);
		verify(http).postJson(eq(AtsProviderEnum.ASHBY), url.capture(), headers.capture(), any());
		assertThat(url.getValue()).isEqualTo("https://api.ashbyhq.com/candidate.list");
		assertThat((String) asMap(headers.getValue()).get("Authorization")).startsWith("Basic ");
	}

	@Test
	void aContinuationCursorIsReplayedAndADurableTimestampStartsFresh() throws Exception {
		when(http.postJson(eq(AtsProviderEnum.ASHBY), contains("/candidate.list"), anyMap(), any()))
			.thenReturn(objectMapper.readTree(
				"{\"results\": [], \"moreDataAvailable\": true, \"nextCursor\": \"opaque-2\"}"));

		var page = connector.listCandidates(credentials, "ashby:opaque-1");

		var request = ArgumentCaptor.forClass(Object.class);
		verify(http).postJson(eq(AtsProviderEnum.ASHBY), anyString(), anyMap(), request.capture());
		assertThat(asMap(request.getValue())).containsEntry("cursor", "opaque-1");
		// The next token stays prefixed so it can't be mistaken for a durable ISO cursor.
		assertThat(page.nextCursor()).isEqualTo("ashby:opaque-2");
	}

	@Test
	void aDurableTimestampCursorIsNotSentAsAnAshbyCursor() throws Exception {
		when(http.postJson(eq(AtsProviderEnum.ASHBY), contains("/candidate.list"), anyMap(), any()))
			.thenReturn(objectMapper.readTree("{\"results\": [], \"moreDataAvailable\": false}"));

		connector.listCandidates(credentials, "2026-09-01T00:00:00Z");

		var request = ArgumentCaptor.forClass(Object.class);
		verify(http).postJson(eq(AtsProviderEnum.ASHBY), anyString(), anyMap(), request.capture());
		assertThat(asMap(request.getValue())).doesNotContainKey("cursor");
	}

	@Test
	void resumeDownloadTradesTheHandleForASignedUrl() throws Exception {
		var candidate = new AtsModels.AtsCandidate(
			"c-1", null, "Ada", null, null, null, "h-resume", "ada_cv.pdf");
		when(http.postJson(eq(AtsProviderEnum.ASHBY), contains("/file.info"), anyMap(), any()))
			.thenReturn(objectMapper.readTree("{\"results\": {\"url\": \"https://files.ashby/x\"}}"));
		when(http.getBytes(eq(AtsProviderEnum.ASHBY), eq("https://files.ashby/x"), anyMap()))
			.thenReturn("pdf-bytes".getBytes(StandardCharsets.UTF_8));

		var file = connector.downloadResume(credentials, candidate);

		assertThat(file).isNotNull();
		assertThat(file.filename()).isEqualTo("ada_cv.pdf");
		// The signed URL is fetched without Basic auth — it carries its own credentials.
		var headers = ArgumentCaptor.forClass(Map.class);
		verify(http).getBytes(eq(AtsProviderEnum.ASHBY), anyString(), headers.capture());
		assertThat(asMap(headers.getValue())).isEmpty();
	}

	@Test
	void aCandidateWithNoResumeIsSkippedWithoutCallingFileInfo() throws QorvaException {
		var candidate = new AtsModels.AtsCandidate("c-2", null, "No File", null, null, null, null, null);

		assertThat(connector.downloadResume(credentials, candidate)).isNull();
		verify(http, org.mockito.Mockito.never()).postJson(any(), anyString(), anyMap(), any());
	}

	@Test
	void jobsMapOpenStatusAndTitle() throws Exception {
		when(http.postJson(eq(AtsProviderEnum.ASHBY), contains("/job.list"), anyMap(), any()))
			.thenReturn(objectMapper.readTree("""
				{"results": [
				  {"id": "j-1", "title": "Backend Engineer", "status": "Open", "departmentName": "Engineering"},
				  {"id": "j-2", "title": "Closed Role", "status": "Closed"}
				], "moreDataAvailable": false}"""));

		var page = connector.listJobs(credentials, null);

		assertThat(page.items()).extracting(AtsModels.AtsJob::open).containsExactly(true, false);
		assertThat(page.items().get(0).description()).isEqualTo("Engineering");
	}

	@Test
	void scoreWriteBackPostsANoteAgainstTheCandidate() throws Exception {
		connector.pushMatchResult(credentials, new AtsModels.MatchWriteBack(
			"c-1", "app-9", "Backend Engineer", 82.0, "Strong match", "https://app.qorva.ai/app/reports"));

		var request = ArgumentCaptor.forClass(Object.class);
		verify(http).postJson(eq(AtsProviderEnum.ASHBY), contains("/candidate.createNote"), anyMap(),
			request.capture());
		var body = asMap(request.getValue());
		assertThat(body).containsEntry("candidateId", "c-1");
		assertThat((String) body.get("note")).contains("Backend Engineer", "82/100", "Strong match");
	}

	@Test
	void webhookWithValidSignatureIsAccepted() {
		var body = """
			{"action":"candidateUpdate","data":{"candidate":{"id":"c-1"}}}"""
			.getBytes(StandardCharsets.UTF_8);
		var secret = "hook-secret";
		var headers = new HttpHeaders();
		headers.set("Ashby-Signature", "sha256=" + AtsWebhookVerifier.hmacHex(body, secret));

		var event = connector.parseWebhook(headers, body, secret);

		assertThat(event).isPresent();
		assertThat(event.get().externalId()).isEqualTo("c-1");
	}

	@Test
	void webhookWithBadOrMissingSignatureIsDropped() {
		var body = "{}".getBytes(StandardCharsets.UTF_8);
		var bad = new HttpHeaders();
		bad.set("Ashby-Signature", "sha256=deadbeef");

		assertThat(connector.parseWebhook(bad, body, "hook-secret")).isEmpty();
		assertThat(connector.parseWebhook(new HttpHeaders(), body, "hook-secret")).isEmpty();
	}
}
