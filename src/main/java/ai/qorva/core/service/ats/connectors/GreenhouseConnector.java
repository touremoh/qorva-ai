package ai.qorva.core.service.ats.connectors;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
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
import ai.qorva.core.service.ats.GreenhouseTokenService;
import ai.qorva.core.service.ats.SyncCursor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Greenhouse Harvest API v3. The tenant creates their own client id and secret in Dev
 * Center ("Unlisted vendor") and Qorva exchanges them for short-lived bearer tokens via the
 * client-credentials grant — no partner approval and no consent redirect.
 *
 * <p>Harvest v1 and v2 were switched off on 31 August 2026, so the API keys those versions
 * issued no longer authenticate anything; connections still holding one surface as an auth
 * error and must be re-created with v3 credentials. Tokens obtained through the older
 * partner authorization-code flow are still honoured where present.</p>
 *
 * <p>Webhooks are signed with the shared secret ("Signature: sha256 &lt;hex&gt;"). Note
 * writes act as the token's own user unless an explicit On-Behalf-Of Greenhouse user id was
 * stored with the credentials.</p>
 */
@Slf4j
@Component
public class GreenhouseConnector implements AtsConnector {

	private static final int PAGE_SIZE = 100;

	private final AtsHttpClient http;
	private final ObjectMapper objectMapper;
	private final GreenhouseTokenService tokenService;
	private final String base;

	public GreenhouseConnector(AtsHttpClient http, ObjectMapper objectMapper, AtsProperties properties,
		GreenhouseTokenService tokenService) {
		this.http = http;
		this.objectMapper = objectMapper;
		this.tokenService = tokenService;
		this.base = trimSlash(properties.getGreenhouseHarvestBaseUrl());
	}

	private static String trimSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	@Override
	public AtsProviderEnum provider() {
		return AtsProviderEnum.GREENHOUSE;
	}

	/**
	 * Every v3 call is a bearer token. It comes from the tenant's client credentials, or is
	 * one already held from the partner authorization-code flow. A stored Harvest v1 API key
	 * is not a fallback — that surface is gone — so it fails here as an auth error rather
	 * than being sent to an endpoint that no longer exists.
	 */
	private Map<String, String> auth(AtsCredentials credentials) throws QorvaException {
		if (tokenService.canMint(credentials)) {
			return Map.of("Authorization", "Bearer " + tokenService.accessToken(credentials));
		}
		if (StringUtils.hasText(credentials.getAccessToken())) {
			return Map.of("Authorization", "Bearer " + credentials.getAccessToken());
		}
		throw new QorvaException(QorvaErrorCodes.ATS_AUTH_FAILED,
			HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
	}

	@Override
	public void validate(AtsCredentials credentials) throws QorvaException {
		http.getJson(provider(), base + "/candidates?per_page=1", auth(credentials));
	}

	@Override
	public AtsPage<AtsJob> listJobs(AtsCredentials credentials, String cursor) throws QorvaException {
		var parsed = SyncCursor.parse(cursor);
		var url = base + "/jobs?per_page=" + PAGE_SIZE + "&page=" + parsed.page();
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
		var url = base + "/candidates?per_page=" + PAGE_SIZE + "&page=" + parsed.page()
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
		// Harvest attributes every write to a Greenhouse user: the same id the v3 token was
		// minted for. A legacy authorization-code token already carries its authorizing user,
		// so only client-credential connections are blocked when no id was stored.
		if (StringUtils.hasText(credentials.getOnBehalfOfUserId())) {
			headers.put("On-Behalf-Of", credentials.getOnBehalfOfUserId());
			note.put("user_id", credentials.getOnBehalfOfUserId());
		} else if (!StringUtils.hasText(credentials.getAccessToken())) {
			log.warn("Greenhouse write-back skipped: no Greenhouse user id stored to attribute the note to");
			return;
		}
		http.postJson(provider(),
			base + "/candidates/" + payload.externalCandidateId() + "/activity_feed/notes",
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
