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
import ai.qorva.core.service.ats.AtsWebhookVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Workable SPI v3: account access token as Bearer, subdomain in the host. Paging is
 * link-based — the API returns paging.next as a complete URL, which is exactly what we
 * store as the continuation cursor (it stays on the fixed workable.com host). Webhooks
 * are signed with X-Workable-Signature (HMAC-SHA256 hex).
 */
@Component
public class WorkableConnector implements AtsConnector {

	private static final int PAGE_SIZE = 100;

	private final AtsHttpClient http;
	private final ObjectMapper objectMapper;

	public WorkableConnector(AtsHttpClient http, ObjectMapper objectMapper) {
		this.http = http;
		this.objectMapper = objectMapper;
	}

	@Override
	public AtsProviderEnum provider() {
		return AtsProviderEnum.WORKABLE;
	}

	private String base(AtsCredentials credentials) {
		return "https://" + credentials.getSubdomain() + ".workable.com/spi/v3";
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
		var url = isContinuation(cursor) ? cursor : base(credentials) + "/jobs?state=published&limit=" + PAGE_SIZE;
		var body = http.getJson(provider(), url, auth(credentials));
		var jobs = new ArrayList<AtsJob>();
		for (JsonNode node : body.path("jobs")) {
			jobs.add(new AtsJob(
				node.path("shortcode").asText(),
				node.path("title").asText(null),
				node.path("full_description").asText(node.path("description").asText(null)),
				"published".equalsIgnoreCase(node.path("state").asText("")),
				parseTime(node.path("created_at").asText(null)),
				node.path("url").asText(null)));
		}
		return new AtsPage<>(jobs, body.path("paging").path("next").asText(null));
	}

	@Override
	public AtsPage<AtsCandidate> listCandidates(AtsCredentials credentials, String cursor) throws QorvaException {
		String url;
		if (isContinuation(cursor)) {
			url = cursor;
		} else {
			url = base(credentials) + "/candidates?limit=" + PAGE_SIZE
				+ (cursor != null && !cursor.isBlank() ? "&updated_after=" + cursor : "");
		}
		var body = http.getJson(provider(), url, auth(credentials));
		var candidates = new ArrayList<AtsCandidate>();
		for (JsonNode node : body.path("candidates")) {
			candidates.add(new AtsCandidate(
				node.path("id").asText(),
				null,
				node.path("name").asText(null),
				node.path("email").asText(null),
				parseTime(node.path("updated_at").asText(null)),
				node.path("profile_url").asText(null),
				null,   // resume link lives on the candidate detail
				null));
		}
		return new AtsPage<>(candidates, body.path("paging").path("next").asText(null));
	}

	/** Continuation cursors are the paging.next URLs Workable itself hands back. */
	private boolean isContinuation(String cursor) {
		return cursor != null && cursor.startsWith("https://");
	}

	@Override
	public AtsFile downloadResume(AtsCredentials credentials, AtsCandidate candidate) throws QorvaException {
		var detail = http.getJson(provider(),
			base(credentials) + "/candidates/" + candidate.externalId(), auth(credentials));
		var url = detail.path("candidate").path("resume_url").asText(null);
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
			base(credentials) + "/candidates/" + payload.externalCandidateId() + "/comments",
			auth(credentials),
			Map.of("body", NoteFormat.text(payload)));
	}

	@Override
	public Optional<AtsWebhookEvent> parseWebhook(HttpHeaders headers, byte[] body, String webhookSecret) {
		var provided = headers.getFirst("X-Workable-Signature");
		if (!AtsWebhookVerifier.matches(AtsWebhookVerifier.hmacHex(body, webhookSecret), provided)) {
			return Optional.empty();
		}
		try {
			var json = objectMapper.readTree(body);
			var candidateId = json.path("data").path("id").asText(null);
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
