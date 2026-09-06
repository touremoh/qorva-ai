package ai.qorva.core.service.ats.connectors;

import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ats.AtsConnector;
import ai.qorva.core.service.ats.AtsCredentials;
import ai.qorva.core.service.ats.AtsHttpClient;
import ai.qorva.core.service.ats.AtsModels.AtsCandidate;
import ai.qorva.core.service.ats.AtsModels.AtsFile;
import ai.qorva.core.service.ats.AtsModels.AtsJob;
import ai.qorva.core.service.ats.AtsModels.AtsPage;
import ai.qorva.core.service.ats.AtsModels.AtsWebhookEvent;
import ai.qorva.core.service.ats.AtsModels.MatchWriteBack;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Manatal Open API v3: "Authorization: Token {key}". DRF-style paging — `next` is a
 * complete URL on the fixed api.manatal.com host, stored verbatim as the continuation
 * cursor. No signed webhooks: the ?token= URL secret is checked in the controller.
 */
@Component
public class ManatalConnector implements AtsConnector {

	private static final String BASE = "https://api.manatal.com/open/v3";
	private static final int PAGE_SIZE = 100;

	private final AtsHttpClient http;
	private final ObjectMapper objectMapper;

	public ManatalConnector(AtsHttpClient http, ObjectMapper objectMapper) {
		this.http = http;
		this.objectMapper = objectMapper;
	}

	@Override
	public AtsProviderEnum provider() {
		return AtsProviderEnum.MANATAL;
	}

	private Map<String, String> auth(AtsCredentials credentials) {
		return Map.of("Authorization", "Token " + credentials.getApiKey());
	}

	@Override
	public void validate(AtsCredentials credentials) throws QorvaException {
		http.getJson(provider(), BASE + "/candidates/?page_size=1", auth(credentials));
	}

	@Override
	public AtsPage<AtsJob> listJobs(AtsCredentials credentials, String cursor) throws QorvaException {
		var url = isContinuation(cursor) ? cursor : BASE + "/jobs/?page_size=" + PAGE_SIZE;
		var body = http.getJson(provider(), url, auth(credentials));
		var jobs = new ArrayList<AtsJob>();
		for (JsonNode node : body.path("results")) {
			jobs.add(new AtsJob(
				node.path("id").asText(),
				node.path("position_name").asText(null),
				node.path("description").asText(null),
				!"closed".equalsIgnoreCase(node.path("status").asText("")),
				parseTime(node.path("updated_at").asText(null)),
				null));
		}
		return new AtsPage<>(jobs, body.path("next").asText(null));
	}

	@Override
	public AtsPage<AtsCandidate> listCandidates(AtsCredentials credentials, String cursor) throws QorvaException {
		var url = isContinuation(cursor) ? cursor : BASE + "/candidates/?page_size=" + PAGE_SIZE;
		var body = http.getJson(provider(), url, auth(credentials));
		var candidates = new ArrayList<AtsCandidate>();
		for (JsonNode node : body.path("results")) {
			candidates.add(new AtsCandidate(
				node.path("id").asText(),
				null,
				node.path("full_name").asText(null),
				node.path("email").asText(null),
				parseTime(node.path("updated_at").asText(null)),
				null,
				node.path("resume").asText(null),
				null));
		}
		return new AtsPage<>(candidates, body.path("next").asText(null));
	}

	private boolean isContinuation(String cursor) {
		return cursor != null && cursor.startsWith("https://");
	}

	@Override
	public AtsFile downloadResume(AtsCredentials credentials, AtsCandidate candidate) throws QorvaException {
		var url = candidate.resumeHandle();
		if (url == null) {
			var detail = http.getJson(provider(),
				BASE + "/candidates/" + candidate.externalId() + "/", auth(credentials));
			url = detail.path("resume").asText(null);
		}
		if (url == null) {
			return null;
		}
		var bytes = http.getBytes(provider(), url, Map.of());
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		return new AtsFile(bytes, "resume.pdf", null);
	}

	@Override
	public void pushMatchResult(AtsCredentials credentials, MatchWriteBack payload) throws QorvaException {
		http.postJson(provider(),
			BASE + "/candidates/" + payload.externalCandidateId() + "/notes/",
			auth(credentials),
			Map.of("content", NoteFormat.text(payload)));
	}

	@Override
	public Optional<AtsWebhookEvent> parseWebhook(HttpHeaders headers, byte[] body, String webhookSecret) {
		// Authenticity was established by the URL token check in the controller.
		try {
			var json = objectMapper.readTree(body);
			var candidateId = json.path("data").path("id").asText(null);
			return Optional.of(new AtsWebhookEvent(json.path("event").asText("event"), candidateId));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private static Instant parseTime(String iso) {
		try {
			return iso == null ? null : Instant.parse(iso);
		} catch (Exception e) {
			return null;
		}
	}
}
