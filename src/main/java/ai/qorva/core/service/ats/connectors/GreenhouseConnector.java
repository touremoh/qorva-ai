package ai.qorva.core.service.ats.connectors;

import ai.qorva.core.config.AtsProperties;
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
import ai.qorva.core.service.ats.SyncCursor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Greenhouse Harvest API (v3), authorized through the Greenhouse OAuth app: the recruiter
 * consents in Greenhouse and the resulting bearer token carries the scopes approved for the
 * app registration. A legacy Harvest API key is still accepted (HTTP Basic, key as username)
 * for connections created before the OAuth switch. Webhooks are signed with the shared
 * secret ("Signature: sha256 <hex>"). Note writes act as the token's own user unless an
 * explicit On-Behalf-Of Greenhouse user id was stored with the credentials.
 */
@Slf4j
@Component
public class GreenhouseConnector implements AtsConnector {

	private static final int PAGE_SIZE = 100;

	private final AtsHttpClient http;
	private final ObjectMapper objectMapper;

	/** Harvest base for OAuth bearer tokens, and for customer API keys — both configurable. */
	private final String oauthBase;
	private final String apiKeyBase;

	public GreenhouseConnector(AtsHttpClient http, ObjectMapper objectMapper, AtsProperties properties) {
		this.http = http;
		this.objectMapper = objectMapper;
		this.oauthBase = trimSlash(properties.getGreenhouseHarvestBaseUrl());
		this.apiKeyBase = trimSlash(properties.getGreenhouseHarvestApiKeyBaseUrl());
	}

	private static String trimSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	/** OAuth and API-key connections can sit on different Harvest versions. */
	private String base(AtsCredentials credentials) {
		return isOauth(credentials) ? oauthBase : apiKeyBase;
	}

	private boolean isOauth(AtsCredentials credentials) {
		return credentials.getAccessToken() != null && !credentials.getAccessToken().isBlank();
	}

	@Override
	public AtsProviderEnum provider() {
		return AtsProviderEnum.GREENHOUSE;
	}

	private Map<String, String> auth(AtsCredentials credentials) {
		if (isOauth(credentials)) {
			return Map.of("Authorization", "Bearer " + credentials.getAccessToken());
		}
		// Customer-generated Harvest API key: Basic with the key as username, empty password.
		var token = Base64.getEncoder()
			.encodeToString((credentials.getApiKey() + ":").getBytes(StandardCharsets.UTF_8));
		return Map.of("Authorization", "Basic " + token);
	}

	@Override
	public void validate(AtsCredentials credentials) throws QorvaException {
		http.getJson(provider(), base(credentials) + "/candidates?per_page=1", auth(credentials));
	}

	@Override
	public AtsPage<AtsJob> listJobs(AtsCredentials credentials, String cursor) throws QorvaException {
		var parsed = SyncCursor.parse(cursor);
		var url = base(credentials) + "/jobs?per_page=" + PAGE_SIZE + "&page=" + parsed.page();
		var body = http.getJson(provider(), url, auth(credentials));
		var jobs = new ArrayList<AtsJob>();
		for (JsonNode node : body) {
			var content = new StringBuilder();
			for (JsonNode dept : node.path("departments")) {
				content.append(dept.path("name").asText("")).append(' ');
			}
			jobs.add(new AtsJob(
				node.path("id").asText(),
				node.path("name").asText(null),
				node.path("notes").asText(content.toString().trim()),
				"open".equalsIgnoreCase(node.path("status").asText("")),
				parseTime(node.path("updated_at").asText(null)),
				null));
		}
		var next = jobs.size() < PAGE_SIZE ? null : SyncCursor.pageToken(parsed.updatedAfter(), parsed.page() + 1);
		return new AtsPage<>(jobs, next);
	}

	@Override
	public AtsPage<AtsCandidate> listCandidates(AtsCredentials credentials, String cursor) throws QorvaException {
		var parsed = SyncCursor.parse(cursor);
		var url = base(credentials) + "/candidates?per_page=" + PAGE_SIZE + "&page=" + parsed.page()
			+ (parsed.updatedAfter() != null ? "&updated_after=" + parsed.updatedAfter() : "");
		var body = http.getJson(provider(), url, auth(credentials));
		var candidates = new ArrayList<AtsCandidate>();
		for (JsonNode node : body) {
			String resumeUrl = null;
			String resumeFilename = null;
			for (JsonNode attachment : node.path("attachments")) {
				if ("resume".equalsIgnoreCase(attachment.path("type").asText(""))) {
					resumeUrl = attachment.path("url").asText(null);
					resumeFilename = attachment.path("filename").asText(null);
					break;
				}
			}
			var applicationId = node.path("application_ids").isArray() && !node.path("application_ids").isEmpty()
				? node.path("application_ids").get(0).asText()
				: null;
			candidates.add(new AtsCandidate(
				node.path("id").asText(),
				applicationId,
				(node.path("first_name").asText("") + " " + node.path("last_name").asText("")).trim(),
				firstEmail(node),
				parseTime(node.path("updated_at").asText(null)),
				null,
				resumeUrl,
				resumeFilename));
		}
		var next = candidates.size() < PAGE_SIZE ? null : SyncCursor.pageToken(parsed.updatedAfter(), parsed.page() + 1);
		return new AtsPage<>(candidates, next);
	}

	private String firstEmail(JsonNode candidate) {
		for (JsonNode address : candidate.path("email_addresses")) {
			var value = address.path("value").asText(null);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	@Override
	public AtsFile downloadResume(AtsCredentials credentials, AtsCandidate candidate) throws QorvaException {
		if (candidate.resumeHandle() == null) {
			return null;
		}
		// Attachment URLs are short-lived S3 links: fetched without Harvest auth headers.
		var bytes = http.getBytes(provider(), candidate.resumeHandle(), Map.of());
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		return new AtsFile(bytes, candidate.resumeFilename() != null ? candidate.resumeFilename() : "resume.pdf", null);
	}

	@Override
	public void pushMatchResult(AtsCredentials credentials, MatchWriteBack payload) throws QorvaException {
		var headers = new java.util.HashMap<>(auth(credentials));
		var note = new java.util.HashMap<String, Object>();
		note.put("body", NoteFormat.text(payload));
		note.put("visibility", "public");
		// OAuth tokens author the note as the authorizing user; a stored user id (API-key
		// connections, or an explicit author) still wins when present.
		if (credentials.getOnBehalfOfUserId() != null && !credentials.getOnBehalfOfUserId().isBlank()) {
			headers.put("On-Behalf-Of", credentials.getOnBehalfOfUserId());
			note.put("user_id", credentials.getOnBehalfOfUserId());
		} else if (!isOauth(credentials)) {
			log.warn("Greenhouse write-back skipped: API-key connection without an On-Behalf-Of user id");
			return;
		}
		http.postJson(provider(),
			base(credentials) + "/candidates/" + payload.externalCandidateId() + "/activity_feed/notes",
			headers, note);
	}

	@Override
	public Optional<AtsWebhookEvent> parseWebhook(HttpHeaders headers, byte[] body, String webhookSecret) {
		var signature = headers.getFirst("Signature");
		if (signature == null) {
			return Optional.empty();
		}
		var provided = signature.replace("sha256", "").trim();
		if (!AtsWebhookVerifier.matches(AtsWebhookVerifier.hmacHex(body, webhookSecret), provided)) {
			return Optional.empty();
		}
		try {
			var json = objectMapper.readTree(body);
			var candidateId = json.path("payload").path("application").path("candidate").path("id").asText(null);
			return Optional.of(new AtsWebhookEvent(json.path("action").asText("event"), candidateId));
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
