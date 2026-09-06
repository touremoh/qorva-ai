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
import ai.qorva.core.service.ats.SyncCursor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Recruitee company API: Bearer personal token, company-scoped paths. No signed
 * webhooks — inbound calls authenticate by the ?token= query secret, checked in
 * AtsPublicController before parseWebhook is called. Offset paging; no updated-after
 * filter, so every run rescans and relies on the engine's per-candidate change skip.
 */
@Component
public class RecruiteeConnector implements AtsConnector {

	private static final String BASE = "https://api.recruitee.com/c/";
	private static final int PAGE_SIZE = 100;

	private final AtsHttpClient http;
	private final ObjectMapper objectMapper;

	public RecruiteeConnector(AtsHttpClient http, ObjectMapper objectMapper) {
		this.http = http;
		this.objectMapper = objectMapper;
	}

	@Override
	public AtsProviderEnum provider() {
		return AtsProviderEnum.RECRUITEE;
	}

	private String base(AtsCredentials credentials) {
		return BASE + credentials.getCompanyId();
	}

	private Map<String, String> auth(AtsCredentials credentials) {
		return Map.of("Authorization", "Bearer " + credentials.getApiKey());
	}

	@Override
	public void validate(AtsCredentials credentials) throws QorvaException {
		http.getJson(provider(), base(credentials) + "/candidates?limit=1", auth(credentials));
	}

	@Override
	public AtsPage<AtsJob> listJobs(AtsCredentials credentials, String cursor) throws QorvaException {
		var body = http.getJson(provider(), base(credentials) + "/offers", auth(credentials));
		var jobs = new ArrayList<AtsJob>();
		for (JsonNode node : body.path("offers")) {
			jobs.add(new AtsJob(
				node.path("id").asText(),
				node.path("title").asText(null),
				node.path("description").asText(null),
				"published".equalsIgnoreCase(node.path("status").asText("")),
				parseTime(node.path("updated_at").asText(null)),
				node.path("careers_url").asText(null)));
		}
		return new AtsPage<>(jobs, null);
	}

	@Override
	public AtsPage<AtsCandidate> listCandidates(AtsCredentials credentials, String cursor) throws QorvaException {
		var parsed = SyncCursor.parse(cursor);
		int offset = (parsed.page() - 1) * PAGE_SIZE;
		var url = base(credentials) + "/candidates?limit=" + PAGE_SIZE + "&offset=" + offset;
		var body = http.getJson(provider(), url, auth(credentials));
		var candidates = new ArrayList<AtsCandidate>();
		for (JsonNode node : body.path("candidates")) {
			candidates.add(new AtsCandidate(
				node.path("id").asText(),
				null,
				node.path("name").asText(null),
				firstOf(node.path("emails")),
				parseTime(node.path("updated_at").asText(null)),
				null,
				node.path("cv_url").asText(null),
				node.path("cv_original_url").asText(null) != null ? fileName(node) : null));
		}
		var next = candidates.size() < PAGE_SIZE ? null : SyncCursor.pageToken(parsed.updatedAfter(), parsed.page() + 1);
		return new AtsPage<>(candidates, next);
	}

	private String fileName(JsonNode node) {
		var url = node.path("cv_original_url").asText("");
		int slash = url.lastIndexOf('/');
		int query = url.indexOf('?', slash);
		return slash >= 0 ? url.substring(slash + 1, query > 0 ? query : url.length()) : "resume.pdf";
	}

	private String firstOf(JsonNode array) {
		return array.isArray() && !array.isEmpty() ? array.get(0).asText(null) : null;
	}

	@Override
	public AtsFile downloadResume(AtsCredentials credentials, AtsCandidate candidate) throws QorvaException {
		// The list payload's cv urls can be stale; re-read the candidate for a fresh link.
		var detail = http.getJson(provider(),
			base(credentials) + "/candidates/" + candidate.externalId(), auth(credentials));
		var url = detail.path("candidate").path("cv_original_url").asText(null);
		if (url == null) {
			url = detail.path("candidate").path("cv_url").asText(null);
		}
		if (url == null) {
			return null;
		}
		var bytes = http.getBytes(provider(), url, Map.of());
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		var filename = candidate.resumeFilename() != null ? candidate.resumeFilename() : "resume.pdf";
		return new AtsFile(bytes, filename, null);
	}

	@Override
	public void pushMatchResult(AtsCredentials credentials, MatchWriteBack payload) throws QorvaException {
		http.postJson(provider(),
			base(credentials) + "/candidates/" + payload.externalCandidateId() + "/notes",
			auth(credentials),
			Map.of("note", Map.of("body", NoteFormat.text(payload))));
	}

	@Override
	public Optional<AtsWebhookEvent> parseWebhook(HttpHeaders headers, byte[] body, String webhookSecret) {
		// Authenticity was established by the URL token check in the controller.
		try {
			var json = objectMapper.readTree(body);
			var candidateId = json.path("payload").path("candidate").path("id").asText(null);
			return Optional.of(new AtsWebhookEvent(json.path("event_type").asText("event"), candidateId));
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
