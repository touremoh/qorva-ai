package ai.qorva.core.service.ats.connectors;

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
import ai.qorva.core.service.ats.AtsModels.WebhookRegistration;
import ai.qorva.core.service.ats.AtsWebhookVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Workable SPI v3: account access token as Bearer, subdomain in the host. Paging is
 * link-based — the API returns paging.next as a complete URL, which is exactly what we
 * store as the continuation cursor (it stays on the fixed workable.com host). Webhooks
 * are signed with X-Workable-Signature (HMAC-SHA256 hex).
 */
@Slf4j
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

	/** Recruiting events; the employee/onboarding ones belong to Workable's HRIS side. */
	private static final List<String> WEBHOOK_EVENTS = List.of("candidate_created", "candidate_moved");

	@Override
	public boolean supportsWebhookRegistration() {
		return true;
	}

	/**
	 * Subscriptions need the numeric account id, which is not the subdomain — it comes from
	 * /accounts and is cached on the credentials so later re-registrations skip the lookup.
	 * Empty job_shortcode and stage_slug mean "every job, every stage".
	 *
	 * <p>Workable answers 409 when the target URL is already subscribed for an event. That is
	 * the desired end state, so it counts as success; the id is simply not recoverable from a
	 * conflict, and the existing subscription keeps working because the URL is unchanged.</p>
	 */
	@Override
	public WebhookRegistration registerWebhooks(AtsCredentials credentials, String callbackUrl, String secret)
		throws QorvaException {
		var accountId = accountId(credentials);
		var ids = new ArrayList<String>();
		for (var event : WEBHOOK_EVENTS) {
			var created = http.postJsonIgnoringConflict(provider(), base(credentials) + "/subscriptions",
				auth(credentials), Map.of(
					"event", event,
					"target", callbackUrl,
					"args", Map.of("account_id", accountId, "job_shortcode", "", "stage_slug", "")));
			if (created.isEmpty()) {
				log.info("Workable subscription for {} already exists on this URL", event);
				continue;
			}
			var id = created.get().path("id").asText(null);
			if (id != null) {
				ids.add(id);
			}
		}
		return WebhookRegistration.of(ids);
	}

	@Override
	public void unregisterWebhooks(AtsCredentials credentials, List<String> externalIds) {
		for (var id : externalIds) {
			try {
				http.delete(provider(), base(credentials) + "/subscriptions/" + id, auth(credentials));
			} catch (Exception e) {
				log.warn("Workable subscription {} could not be removed: {}", id, e.getMessage());
			}
		}
	}

	/** Resolves and caches the account id the subscriptions API insists on. */
	private String accountId(AtsCredentials credentials) throws QorvaException {
		if (StringUtils.hasText(credentials.getAccountId())) {
			return credentials.getAccountId();
		}
		var body = http.getJson(provider(), base(credentials) + "/accounts", auth(credentials));
		var accounts = body.path("accounts");
		var id = accounts.isArray() && !accounts.isEmpty()
			? accounts.get(0).path("id").asText(null)
			: body.path("id").asText(null);
		if (!StringUtils.hasText(id)) {
			throw new QorvaException(QorvaErrorCodes.ATS_API_ERROR,
				HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY);
		}
		credentials.setAccountId(id);
		return id;
	}

	/**
	 * Workable signs with the account token that created the subscription — not with a secret
	 * we choose — so the connection's own webhookSecret is the wrong key here. The engine
	 * passes the API key in as webhookSecret for this provider; see AtsWebhookService.
	 */
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
