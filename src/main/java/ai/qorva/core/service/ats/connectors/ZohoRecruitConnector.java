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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Zoho Recruit v2 REST. OAuth2 with per-datacenter hosts — the datacenter from the
 * grant is stored in credentials and every URL is built from it. Access tokens are
 * refreshed by AtsOauthService before a run; this connector only spends them.
 * Webhooks (notification API) carry no signature: the ?token= URL secret is checked
 * in the controller.
 */
@Component
public class ZohoRecruitConnector implements AtsConnector {

	private static final int PAGE_SIZE = 100;

	private final AtsHttpClient http;
	private final ObjectMapper objectMapper;

	public ZohoRecruitConnector(AtsHttpClient http, ObjectMapper objectMapper) {
		this.http = http;
		this.objectMapper = objectMapper;
	}

	@Override
	public AtsProviderEnum provider() {
		return AtsProviderEnum.ZOHO_RECRUIT;
	}

	/**
	 * API host for this grant. api_domain comes straight from Zoho's token response and is
	 * correct for every datacenter, including ones added after this code was written; the
	 * datacenter key is only a fallback for grants issued before it was captured.
	 */
	private String base(AtsCredentials credentials) {
		var apiDomain = credentials.getApiDomain();
		if (apiDomain != null && !apiDomain.isBlank()) {
			return apiDomain.replaceAll("/+$", "") + "/recruit/v2";
		}
		var dc = credentials.getDatacenter() != null ? credentials.getDatacenter() : "com";
		return "https://recruit.zoho." + dc + "/recruit/v2";
	}

	private Map<String, String> auth(AtsCredentials credentials) {
		return Map.of("Authorization", "Zoho-oauthtoken " + credentials.getAccessToken());
	}

	@Override
	public void validate(AtsCredentials credentials) throws QorvaException {
		http.getJson(provider(), base(credentials) + "/Candidates?per_page=1", auth(credentials));
	}

	@Override
	public AtsPage<AtsJob> listJobs(AtsCredentials credentials, String cursor) throws QorvaException {
		var parsed = SyncCursor.parse(cursor);
		var url = base(credentials) + "/Job_Openings?per_page=" + PAGE_SIZE + "&page=" + parsed.page();
		var body = http.getJson(provider(), url, auth(credentials));
		var jobs = new ArrayList<AtsJob>();
		for (JsonNode node : body.path("data")) {
			var status = node.path("Job_Opening_Status").asText("");
			jobs.add(new AtsJob(
				node.path("id").asText(),
				node.path("Job_Opening_Name").asText(node.path("Posting_Title").asText(null)),
				node.path("Job_Description").asText(null),
				"In-progress".equalsIgnoreCase(status) || "Open".equalsIgnoreCase(status),
				parseTime(node.path("Modified_Time").asText(null)),
				null));
		}
		var more = body.path("info").path("more_records").asBoolean(false);
		return new AtsPage<>(jobs, more ? SyncCursor.pageToken(parsed.updatedAfter(), parsed.page() + 1) : null);
	}

	@Override
	public AtsPage<AtsCandidate> listCandidates(AtsCredentials credentials, String cursor) throws QorvaException {
		var parsed = SyncCursor.parse(cursor);
		var url = base(credentials) + "/Candidates?per_page=" + PAGE_SIZE + "&page=" + parsed.page()
			+ "&sort_by=Modified_Time&sort_order=desc";
		var headers = new java.util.HashMap<>(auth(credentials));
		if (parsed.updatedAfter() != null) {
			headers.put("If-Modified-Since", parsed.updatedAfter());
		}
		var body = http.getJson(provider(), url, headers);
		var candidates = new ArrayList<AtsCandidate>();
		for (JsonNode node : body.path("data")) {
			candidates.add(new AtsCandidate(
				node.path("id").asText(),
				null,
				node.path("Full_Name").asText(null),
				node.path("Email").asText(null),
				parseTime(node.path("Modified_Time").asText(null)),
				null,
				null,
				null));
		}
		var more = body.path("info").path("more_records").asBoolean(false);
		return new AtsPage<>(candidates, more ? SyncCursor.pageToken(parsed.updatedAfter(), parsed.page() + 1) : null);
	}

	@Override
	public AtsFile downloadResume(AtsCredentials credentials, AtsCandidate candidate) throws QorvaException {
		var attachments = http.getJson(provider(),
			base(credentials) + "/Candidates/" + candidate.externalId() + "/Attachments", auth(credentials));
		String attachmentId = null;
		String filename = null;
		for (JsonNode node : attachments.path("data")) {
			var category = node.path("$attachment_category").path("api_name")
				.asText(node.path("Category").asText(""));
			if (attachmentId == null || category.toLowerCase().contains("resume")) {
				attachmentId = node.path("id").asText(null);
				filename = node.path("File_Name").asText(null);
				if (category.toLowerCase().contains("resume")) {
					break;
				}
			}
		}
		if (attachmentId == null) {
			return null;
		}
		var bytes = http.getBytes(provider(),
			base(credentials) + "/Candidates/" + candidate.externalId() + "/Attachments/" + attachmentId,
			auth(credentials));
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		return new AtsFile(bytes, filename != null ? filename : "resume.pdf", null);
	}

	@Override
	public void pushMatchResult(AtsCredentials credentials, MatchWriteBack payload) throws QorvaException {
		var note = Map.of(
			"Note_Title", "Qorva match score",
			"Note_Content", NoteFormat.text(payload),
			"Parent_Id", payload.externalCandidateId(),
			"se_module", "Candidates");
		http.postJson(provider(), base(credentials) + "/Notes", auth(credentials),
			Map.of("data", List.of(note)));
	}

	@Override
	public Optional<AtsWebhookEvent> parseWebhook(HttpHeaders headers, byte[] body, String webhookSecret) {
		// Authenticity was established by the URL token check in the controller.
		try {
			var json = objectMapper.readTree(body);
			var candidateId = json.path("id").asText(null);
			return Optional.of(new AtsWebhookEvent(json.path("operation").asText("event"), candidateId));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private static Instant parseTime(String value) {
		if (value == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (Exception e) {
			try {
				return Instant.parse(value);
			} catch (Exception inner) {
				return null;
			}
		}
	}
}
