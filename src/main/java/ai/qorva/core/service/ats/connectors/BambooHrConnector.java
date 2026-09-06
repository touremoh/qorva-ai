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
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * BambooHR ATS endpoints: Basic auth (api key : x), subdomain as a path segment.
 * The applications list is applicant-tracking's canonical stream; BambooHR webhooks
 * cover HRIS fields, not ATS events, so this connector is polling-only (parseWebhook
 * always declines). One application = one candidate here — the applicant has no
 * standalone id in the v1 ATS API.
 */
@Component
public class BambooHrConnector implements AtsConnector {

	private static final int PAGE_SIZE = 100;

	private final AtsHttpClient http;

	public BambooHrConnector(AtsHttpClient http) {
		this.http = http;
	}

	@Override
	public AtsProviderEnum provider() {
		return AtsProviderEnum.BAMBOOHR;
	}

	private String base(AtsCredentials credentials) {
		return "https://api.bamboohr.com/api/gateway.php/" + credentials.getSubdomain() + "/v1";
	}

	private Map<String, String> auth(AtsCredentials credentials) {
		var token = Base64.getEncoder()
			.encodeToString((credentials.getApiKey() + ":x").getBytes(StandardCharsets.UTF_8));
		return Map.of("Authorization", "Basic " + token, "Accept", "application/json");
	}

	@Override
	public void validate(AtsCredentials credentials) throws QorvaException {
		http.getJson(provider(), base(credentials) + "/applicant_tracking/applications?page=1", auth(credentials));
	}

	@Override
	public AtsPage<AtsJob> listJobs(AtsCredentials credentials, String cursor) throws QorvaException {
		var body = http.getJson(provider(),
			base(credentials) + "/applicant_tracking/jobs?statusGroups=Open", auth(credentials));
		var jobs = new ArrayList<AtsJob>();
		for (JsonNode node : body) {
			jobs.add(new AtsJob(
				node.path("id").asText(),
				node.path("title").path("label").asText(node.path("title").asText(null)),
				node.path("description").asText(null),
				true,
				null,
				null));
		}
		return new AtsPage<>(jobs, null);
	}

	@Override
	public AtsPage<AtsCandidate> listCandidates(AtsCredentials credentials, String cursor) throws QorvaException {
		var parsed = SyncCursor.parse(cursor);
		var url = base(credentials) + "/applicant_tracking/applications?page=" + parsed.page();
		var body = http.getJson(provider(), url, auth(credentials));
		var candidates = new ArrayList<AtsCandidate>();
		for (JsonNode node : body.path("applications")) {
			var applicant = node.path("applicant");
			candidates.add(new AtsCandidate(
				node.path("id").asText(),
				node.path("id").asText(),
				(applicant.path("firstName").asText("") + " " + applicant.path("lastName").asText("")).trim(),
				applicant.path("email").asText(null),
				parseTime(node.path("appliedDate").asText(null)),
				null,
				null,
				null));
		}
		String next = null;
		var totalPages = body.path("paginationComplete").isMissingNode()
			? body.path("totalPages").asInt(parsed.page())
			: parsed.page();
		if (candidates.size() == PAGE_SIZE || totalPages > parsed.page()) {
			next = SyncCursor.pageToken(parsed.updatedAfter(), parsed.page() + 1);
		}
		return new AtsPage<>(candidates, next);
	}

	@Override
	public AtsFile downloadResume(AtsCredentials credentials, AtsCandidate candidate) throws QorvaException {
		var detail = http.getJson(provider(),
			base(credentials) + "/applicant_tracking/applications/" + candidate.externalId(), auth(credentials));
		var resumeId = detail.path("applicant").path("resumeFileId").asText(null);
		if (resumeId == null || resumeId.isBlank() || "null".equals(resumeId)) {
			return null;
		}
		var bytes = http.getBytes(provider(),
			base(credentials) + "/files/" + resumeId, auth(credentials));
		if (bytes == null || bytes.length == 0) {
			return null;
		}
		return new AtsFile(bytes, "resume.pdf", null);
	}

	@Override
	public void pushMatchResult(AtsCredentials credentials, MatchWriteBack payload) throws QorvaException {
		http.postJson(provider(),
			base(credentials) + "/applicant_tracking/applications/" + payload.externalCandidateId() + "/comments",
			auth(credentials),
			Map.of("type", "comment", "comment", NoteFormat.text(payload)));
	}

	@Override
	public Optional<AtsWebhookEvent> parseWebhook(HttpHeaders headers, byte[] body, String webhookSecret) {
		return Optional.empty();
	}

	private static Instant parseTime(String iso) {
		try {
			return iso == null ? null : Instant.parse(iso);
		} catch (Exception e) {
			return null;
		}
	}
}
