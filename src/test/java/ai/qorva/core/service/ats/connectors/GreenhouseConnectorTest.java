package ai.qorva.core.service.ats.connectors;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ats.AtsCredentials;
import ai.qorva.core.service.ats.AtsHttpClient;
import ai.qorva.core.service.ats.AtsWebhookVerifier;
import ai.qorva.core.service.ats.GreenhouseTokenService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GreenhouseConnectorTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock private AtsHttpClient http;
	@Mock private GreenhouseTokenService tokenService;

	private GreenhouseConnector connector;
	private final AtsCredentials credentials = AtsCredentials.builder().accessToken("gh-access-token").build();

	@BeforeEach
	void setUp() {
		connector = new GreenhouseConnector(http, objectMapper, new AtsProperties(), tokenService);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object captured) {
		return (Map<String, Object>) captured;
	}

	@Test
	void oauthCredentialsAuthorizeWithABearerToken() throws Exception {
		when(http.getJson(eq(AtsProviderEnum.GREENHOUSE), contains("/candidates?"), anyMap()))
			.thenReturn(objectMapper.readTree("[]"));

		connector.listCandidates(credentials, null);

		var headers = ArgumentCaptor.forClass(Map.class);
		var url = ArgumentCaptor.forClass(String.class);
		verify(http).getJson(eq(AtsProviderEnum.GREENHOUSE), url.capture(), headers.capture());
		assertThat(asMap(headers.getValue())).containsEntry("Authorization", "Bearer gh-access-token");
		assertThat(url.getValue()).startsWith("https://harvest.greenhouse.io/v3/candidates");
	}

	@Test
	void clientCredentialsAreExchangedForABearerTokenOnTheV3Base() throws Exception {
		var clientCredentials = AtsCredentials.builder().clientId("id").clientSecret("secret").build();
		when(tokenService.canMint(clientCredentials)).thenReturn(true);
		when(tokenService.accessToken(clientCredentials)).thenReturn("minted-token");
		when(http.getJson(eq(AtsProviderEnum.GREENHOUSE), contains("/candidates?"), anyMap()))
			.thenReturn(objectMapper.readTree("[]"));

		connector.listCandidates(clientCredentials, null);

		var headers = ArgumentCaptor.forClass(Map.class);
		var url = ArgumentCaptor.forClass(String.class);
		verify(http).getJson(eq(AtsProviderEnum.GREENHOUSE), url.capture(), headers.capture());
		assertThat(asMap(headers.getValue())).containsEntry("Authorization", "Bearer minted-token");
		assertThat(url.getValue()).startsWith("https://harvest.greenhouse.io/v3/candidates");
	}

	/**
	 * Harvest v1 and v2 were switched off on 31 August 2026. A connection still holding one
	 * of their API keys has nothing left to authenticate with, so it must fail as an auth
	 * error rather than quietly calling a v3 endpoint the key was never valid for.
	 */
	@Test
	void aLegacyHarvestApiKeyNoLongerAuthenticates() {
		var legacy = AtsCredentials.builder().apiKey("v1-key").build();

		assertThatThrownBy(() -> connector.listCandidates(legacy, null))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.ATS_AUTH_FAILED);
	}

	@Test
	void noteWriteBackOnOauthOmitsOnBehalfOfAndStillPosts() throws Exception {
		connector.pushMatchResult(credentials, new ai.qorva.core.service.ats.AtsModels.MatchWriteBack(
			"17681532", "59724", "Backend Engineer", 82.0, "Strong match", "https://app.qorva.ai/report/1"));

		var headers = ArgumentCaptor.forClass(Map.class);
		var body = ArgumentCaptor.forClass(Object.class);
		verify(http).postJson(eq(AtsProviderEnum.GREENHOUSE),
			contains("/candidates/17681532/activity_feed/notes"), headers.capture(), body.capture());
		assertThat(asMap(headers.getValue())).doesNotContainKey("On-Behalf-Of");
		assertThat(asMap(body.getValue())).doesNotContainKey("user_id");
	}

	@Test
	void noteWriteBackKeepsOnBehalfOfWhenConfigured() throws Exception {
		var withAuthor = AtsCredentials.builder().accessToken("t").onBehalfOfUserId("4321").build();

		connector.pushMatchResult(withAuthor, new ai.qorva.core.service.ats.AtsModels.MatchWriteBack(
			"17681532", null, "Backend Engineer", 82.0, "Strong match", null));

		var headers = ArgumentCaptor.forClass(Map.class);
		var body = ArgumentCaptor.forClass(Object.class);
		verify(http).postJson(eq(AtsProviderEnum.GREENHOUSE), anyString(), headers.capture(), body.capture());
		assertThat(asMap(headers.getValue())).containsEntry("On-Behalf-Of", "4321");
		assertThat(asMap(body.getValue())).containsEntry("user_id", "4321");
	}

	@Test
	void listCandidatesMapsResumeAttachmentAndEmail() throws Exception {
		var payload = """
			[{
			  "id": 17681532,
			  "first_name": "Ada",
			  "last_name": "Lovelace",
			  "updated_at": "2026-08-20T10:15:30Z",
			  "application_ids": [59724],
			  "email_addresses": [{"value": "ada@example.com", "type": "personal"}],
			  "attachments": [
			    {"filename": "cover.pdf", "url": "https://s3/cover", "type": "cover_letter"},
			    {"filename": "ada_cv.pdf", "url": "https://s3/resume", "type": "resume"}
			  ]
			}]""";
		when(http.getJson(eq(AtsProviderEnum.GREENHOUSE), contains("/candidates?"), anyMap()))
			.thenReturn(objectMapper.readTree(payload));

		var page = connector.listCandidates(credentials, null);

		assertThat(page.items()).hasSize(1);
		var candidate = page.items().get(0);
		assertThat(candidate.externalId()).isEqualTo("17681532");
		assertThat(candidate.name()).isEqualTo("Ada Lovelace");
		assertThat(candidate.email()).isEqualTo("ada@example.com");
		assertThat(candidate.externalApplicationId()).isEqualTo("59724");
		assertThat(candidate.resumeHandle()).isEqualTo("https://s3/resume");
		assertThat(candidate.resumeFilename()).isEqualTo("ada_cv.pdf");
		// A short page ends the stream.
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void candidateWithoutResumeHasNullHandleAndDownloadReturnsNull() throws QorvaException {
		var candidate = new ai.qorva.core.service.ats.AtsModels.AtsCandidate(
			"1", null, "No File", null, null, null, null, null);
		assertThat(connector.downloadResume(credentials, candidate)).isNull();
	}

	@Test
	void webhookWithValidSignatureIsAccepted() throws Exception {
		var body = """
			{"action":"candidate_updated","payload":{"application":{"candidate":{"id":42}}}}"""
			.getBytes(StandardCharsets.UTF_8);
		var secret = "hook-secret";
		var headers = new HttpHeaders();
		headers.set("Signature", "sha256 " + AtsWebhookVerifier.hmacHex(body, secret));

		var event = connector.parseWebhook(headers, body, secret);

		assertThat(event).isPresent();
		assertThat(event.get().externalId()).isEqualTo("42");
	}

	@Test
	void webhookWithBadSignatureIsDropped() {
		var body = "{}".getBytes(StandardCharsets.UTF_8);
		var headers = new HttpHeaders();
		headers.set("Signature", "sha256 deadbeef");

		assertThat(connector.parseWebhook(headers, body, "hook-secret")).isEmpty();
	}

	@Test
	void webhookWithoutSignatureHeaderIsDropped() {
		assertThat(connector.parseWebhook(new HttpHeaders(), "{}".getBytes(StandardCharsets.UTF_8), "s"))
			.isEmpty();
	}
}
