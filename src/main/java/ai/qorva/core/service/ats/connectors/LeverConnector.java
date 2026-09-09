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
import ai.qorva.core.service.ats.AtsModels.WebhookRegistration;
import ai.qorva.core.service.ats.AtsWebhookVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lever v1, authenticating either way the customer can offer.
 *
 * <p>An API key a Super Admin generates in Settings → Integrations and API → API
 * Credentials is HTTP Basic with the key as the username and an empty password. That path
 * needs nothing from Lever beyond the customer's own admin rights, so it is what tenants
 * use until Qorva is an approved Lever partner. Partner OAuth bearer tokens are used when
 * present and refreshed by AtsOauthService.</p>
 *
 * <p>Opportunities are the candidate stream; offset paging via the API's own next token,
 * delta via updated_at_start (epoch millis). Webhook payloads are signed: token +
 * triggeredAt HMAC-signed with the webhook's signature secret.</p>
 */
@Slf4j
@Component
public class LeverConnector implements AtsConnector {

	private static final String BASE = "https://api.lever.co/v1";
	private static final int PAGE_SIZE = 100;
	private static final String OFFSET_PREFIX = "offset:";

	private final AtsHttpClient http;
	private final ObjectMapper objectMapper;

	public LeverConnector(AtsHttpClient http, ObjectMapper objectMapper) {
		this.http = http;
		this.objectMapper = objectMapper;
	}

	@Override
	public AtsProviderEnum provider() {
		return AtsProviderEnum.LEVER;
	}

	private Map<String, String> auth(AtsCredentials credentials) {
		if (StringUtils.hasText(credentials.getAccessToken())) {
			return Map.of("Authorization", "Bearer " + credentials.getAccessToken());
		}
		// Customer-generated API key: Basic with the key as username and no password.
		var token = Base64.getEncoder()
			.encodeToString((credentials.getApiKey() + ":").getBytes(StandardCharsets.UTF_8));
		return Map.of("Authorization", "Basic " + token);
	}

	@Override
	public void validate(AtsCredentials credentials) throws QorvaException {
		http.getJson(provider(), BASE + "/opportunities?limit=1", auth(credentials));
	}

	@Override
	public AtsPage<AtsJob> listJobs(AtsCredentials credentials, String cursor) throws QorvaException {
		var url = BASE + "/postings?state=published&limit=" + PAGE_SIZE
			+ (isOffset(cursor) ? "&offset=" + cursor.substring(OFFSET_PREFIX.length()) : "");
		var body = http.getJson(provider(), url, auth(credentials));
		var jobs = new ArrayList<AtsJob>();
		for (JsonNode node : body.path("data")) {
			jobs.add(new AtsJob(
				node.path("id").asText(),
				node.path("text").asText(null),
				node.path("content").path("description").asText(null),
				"published".equalsIgnoreCase(node.path("state").asText("")),
				epoch(node.path("updatedAt")),
				node.path("urls").path("show").asText(null)));
		}
		return new AtsPage<>(jobs, nextToken(body));
	}

	@Override
	public AtsPage<AtsCandidate> listCandidates(AtsCredentials credentials, String cursor) throws QorvaException {
		var url = new StringBuilder(BASE + "/opportunities?limit=" + PAGE_SIZE + "&expand=contact");
		if (isOffset(cursor)) {
			url.append("&offset=").append(cursor.substring(OFFSET_PREFIX.length()));
		} else if (cursor != null && !cursor.isBlank()) {
			// Durable cursor is the ISO timestamp of the last drained run.
			url.append("&updated_at_start=").append(Instant.parse(cursor).toEpochMilli());
		}
		var body = http.getJson(provider(), url.toString(), auth(credentials));
		var candidates = new ArrayList<AtsCandidate>();
		for (JsonNode node : body.path("data")) {
			var contact = node.path("contact");
			candidates.add(new AtsCandidate(
				node.path("id").asText(),
				node.path("id").asText(),
				node.path("name").asText(contact.path("name").asText(null)),
				firstOf(node.path("emails")),
				epoch(node.path("updatedAt")),
				node.path("urls").path("show").asText(null),
				null,
				null));
		}
		return new AtsPage<>(candidates, nextToken(body));
	}

	private String nextToken(JsonNode body) {
		return body.path("hasNext").asBoolean(false) ? OFFSET_PREFIX + body.path("next").asText("") : null;
	}

	private boolean isOffset(String cursor) {
		return cursor != null && cursor.startsWith(OFFSET_PREFIX);
	}

	private String firstOf(JsonNode array) {
		return array.isArray() && !array.isEmpty() ? array.get(0).asText(null) : null;
	}

	@Override
	public AtsFile downloadResume(AtsCredentials credentials, AtsCandidate candidate) throws QorvaException {
		var resumes = http.getJson(provider(),
			BASE + "/opportunities/" + candidate.externalId() + "/resumes", auth(credentials));
		var data = resumes.path("data");
		if (!data.isArray() || data.isEmpty()) {
			return null;
		}
		var resume = data.get(0);
		var resumeId = resume.path("id").asText(null);
		if (resumeId == null) {
			return null;
		}
		var bytes = http.getBytes(provider(),
			BASE + "/opportunities/" + candidate.externalId() + "/resumes/" + resumeId + "/download",
			auth(credentials));
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		var filename = resume.path("file").path("name").asText("resume.pdf");
		return new AtsFile(bytes, filename, resume.path("file").path("ext").asText(null));
	}

	@Override
	public void pushMatchResult(AtsCredentials credentials, MatchWriteBack payload) throws QorvaException {
		http.postJson(provider(),
			BASE + "/opportunities/" + payload.externalCandidateId() + "/notes",
			auth(credentials),
			Map.of("value", NoteFormat.text(payload)));
	}

	/** Events that change where a candidate stands; contact/interview noise is left out. */
	private static final List<String> WEBHOOK_EVENTS =
		List.of("applicationCreated", "candidateStageChange", "candidateHired", "candidateArchiveChange");

	@Override
	public boolean supportsWebhookRegistration() {
		return true;
	}

	/**
	 * Lever creates the subscription happily, but the signing token is the awkward part: it
	 * belongs to the account, not the webhook, and Lever's own documentation points at the
	 * settings screen for it. Some responses do carry it under configuration.signatureToken,
	 * so it is read back opportunistically and returned for storage; when it is absent the
	 * caller keeps whatever the tenant pasted in by hand, and a connection with neither
	 * cannot verify a delivery.
	 */
	@Override
	public WebhookRegistration registerWebhooks(AtsCredentials credentials, String callbackUrl, String secret)
		throws QorvaException {
		var ids = new ArrayList<String>();
		String signingSecret = null;
		for (var event : WEBHOOK_EVENTS) {
			var body = http.postJson(provider(), BASE + "/webhooks", auth(credentials), Map.of(
				"url", callbackUrl,
				"event", event));
			var data = body.has("data") ? body.path("data") : body;
			var id = data.path("id").asText(null);
			if (id != null) {
				ids.add(id);
			}
			var token = data.path("configuration").path("signatureToken").asText(
				data.path("signatureToken").asText(null));
			if (StringUtils.hasText(token)) {
				signingSecret = token;
			}
		}
		return new WebhookRegistration(ids, signingSecret);
	}

	@Override
	public void unregisterWebhooks(AtsCredentials credentials, List<String> externalIds) {
		for (var id : externalIds) {
			try {
				http.delete(provider(), BASE + "/webhooks/" + id, auth(credentials));
			} catch (Exception e) {
				log.warn("Lever webhook {} could not be removed: {}", id, e.getMessage());
			}
		}
	}

	/**
	 * Verified with Lever's account signing token, not the connection's generated secret —
	 * Lever picks the key. AtsWebhookService passes the stored token in as webhookSecret.
	 */
	@Override
	public Optional<AtsWebhookEvent> parseWebhook(HttpHeaders headers, byte[] body, String webhookSecret) {
		try {
			var json = objectMapper.readTree(body);
			var token = json.path("token").asText(null);
			var triggeredAt = json.path("triggeredAt").asText(null);
			var provided = json.path("signature").asText(null);
			if (token == null || triggeredAt == null || provided == null) {
				return Optional.empty();
			}
			var expected = AtsWebhookVerifier.hmacHex(
				(token + triggeredAt).getBytes(java.nio.charset.StandardCharsets.UTF_8), webhookSecret);
			if (!AtsWebhookVerifier.matches(expected, provided)) {
				return Optional.empty();
			}
			var opportunityId = json.path("data").path("opportunityId").asText(null);
			return Optional.of(new AtsWebhookEvent(json.path("event").asText("event"), opportunityId));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private static Instant epoch(JsonNode millis) {
		return millis.isNumber() ? Instant.ofEpochMilli(millis.asLong()) : null;
	}
}
