package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dto.BackgroundJobData;
import ai.qorva.core.dto.CandidateOutreachData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.service.AIScreeningService;
import ai.qorva.core.service.CandidateOutreachService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.MatchingTopNPolicy;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.agent.AgentApproval;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.ats.AtsConnectionService;
import ai.qorva.core.service.ats.AtsSyncService;
import ai.qorva.core.service.mailbox.MailboxConnectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalToolsTest {

	private static final String TENANT = "64b7f0f0f0f0f0f0f0f0f0f0";
	private static final AgentToolContext CTX = new AgentToolContext(TENANT, "owner@a.test", "en", null, "run-1");
	private static final ObjectMapper JSON = new ObjectMapper();

	@Mock private CandidateOutreachService outreachService;
	@Mock private MailboxConnectionService mailboxService;
	@Mock private AIScreeningService screeningService;
	@Mock private JobPostService jobPostService;
	@Mock private UsageMonitoringService usageMonitoringService;
	@Mock private MatchingTopNPolicy topNPolicy;
	@Mock private AtsConnectionService connectionService;
	@Mock private AtsSyncService syncService;

	private static JsonNode args(String json) throws Exception {
		return JSON.readTree(json);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object data) {
		return (Map<String, Object>) data;
	}

	private static CandidateOutreachData.ContextResponse context(String email, boolean suppressed, CandidateOutreachData.MailboxState mailbox) {
		return new CandidateOutreachData.ContextResponse("Ana Ruiz", email, suppressed, mailbox, "owner@a.test", List.of());
	}

	private static final String SEND_ARGS = "{\"cvId\":\"cv-1\",\"subject\":\"Hello\",\"body\":\"Intro\",\"to\":\"attacker@evil.test\"}";

	// ---- send_outreach_email ---------------------------------------------------------------

	@Test
	void theCardShowsTheAddressFromTheProfileNeverOneFromTheModel() throws Exception {
		when(outreachService.context(TENANT, "owner@a.test", "cv-1"))
			.thenReturn(context("ana@x.test", false, CandidateOutreachData.MailboxState.MICROSOFT));
		var tool = new SendOutreachEmailTool(outreachService, mailboxService);

		var card = tool.preview(args(SEND_ARGS), CTX);

		assertThat(card.ok()).isTrue();
		assertThat(map(card.data())).containsEntry("to", "ana@x.test").containsEntry("from", "owner@a.test")
			.containsEntry("subject", "Hello").containsEntry("candidateName", "Ana Ruiz");
		assertThat(card.data().toString()).doesNotContain("attacker");
		assertThat(tool.outbound()).isTrue();
	}

	@Test
	void aSuppressedCandidateNoAddressOrNoMailboxNeverBecomesACard() throws Exception {
		var tool = new SendOutreachEmailTool(outreachService, mailboxService);

		when(outreachService.context(any(), any(), any())).thenReturn(context("ana@x.test", true, CandidateOutreachData.MailboxState.MICROSOFT));
		assertThat(tool.preview(args(SEND_ARGS), CTX).ok()).isFalse();
		when(outreachService.context(any(), any(), any())).thenReturn(context(null, false, CandidateOutreachData.MailboxState.MICROSOFT));
		assertThat(tool.preview(args(SEND_ARGS), CTX).ok()).isFalse();
		when(outreachService.context(any(), any(), any())).thenReturn(context("ana@x.test", false, CandidateOutreachData.MailboxState.REAUTH_REQUIRED));
		assertThat(tool.preview(args(SEND_ARGS), CTX).ok()).isFalse();
		assertThat(tool.preview(args("{\"cvId\":\"cv-1\",\"subject\":\"" + "x".repeat(201) + "\",\"body\":\"b\"}"), CTX).ok()).isFalse();
	}

	@Test
	void sendingUsesTheEditsTheProfileAddressAndRecordsTheRun() throws Exception {
		when(outreachService.context(TENANT, "owner@a.test", "cv-1"))
			.thenReturn(context("ana@x.test", false, CandidateOutreachData.MailboxState.MICROSOFT));
		var tool = new SendOutreachEmailTool(outreachService, mailboxService);

		var result = tool.execute(args(SEND_ARGS), CTX, new AgentApproval(null, "Intro, edited by the recruiter"));

		var request = ArgumentCaptor.forClass(CandidateOutreachData.SendRequest.class);
		verify(outreachService).send(eq(TENANT), eq("owner@a.test"), request.capture(), eq("run-1"));
		assertThat(request.getValue().getTo()).isEqualTo("ana@x.test");
		assertThat(request.getValue().getSubject()).isEqualTo("Hello");
		assertThat(request.getValue().getBody()).isEqualTo("Intro, edited by the recruiter");
		assertThat(map(result.data())).containsEntry("sent", true).containsEntry("editedByRecruiter", true);
	}

	@Test
	void withoutApprovalNothingIsSent() throws Exception {
		var result = new SendOutreachEmailTool(outreachService, mailboxService).execute(args(SEND_ARGS), CTX);

		assertThat(result.ok()).isFalse();
		verify(outreachService, never()).send(any(), any(), any(), any());
	}

	@Test
	void theToolIsOfferedOnlyWithAConnectedMailbox() {
		var tool = new SendOutreachEmailTool(outreachService, mailboxService);
		when(mailboxService.composerState(TENANT, "owner@a.test"))
			.thenReturn(new MailboxConnectionService.ComposerState(CandidateOutreachData.MailboxState.NONE, null));
		assertThat(tool.available(CTX)).isFalse();
		when(mailboxService.composerState(TENANT, "owner@a.test"))
			.thenReturn(new MailboxConnectionService.ComposerState(CandidateOutreachData.MailboxState.MICROSOFT, "owner@a.test"));
		assertThat(tool.available(CTX)).isTrue();
	}

	// ---- start_screening -------------------------------------------------------------------

	private static JobPostDTO job(String id, String status) {
		var job = new JobPostDTO();
		job.setId(id);
		job.setTitle("Backend Lead");
		job.setStatus(status);
		return job;
	}

	private StartScreeningTool startScreening() {
		return new StartScreeningTool(screeningService, jobPostService, usageMonitoringService, topNPolicy);
	}

	/** One open job whose top 10 holds 7 new candidates and 3 unchanged reports. */
	private void givenOpenJobWithSevenNewReports() throws Exception {
		when(jobPostService.findOneById("job-1")).thenReturn(job("job-1", "open"));
		when(topNPolicy.limitsFor(TENANT)).thenReturn(new MatchingTopNPolicy.Limits(20, 10));
		when(topNPolicy.resolve(eq(TENANT), any(), any())).thenReturn(10);
		when(screeningService.estimate(any(), eq(10), eq("en")))
			.thenReturn(List.of(new AIScreeningService.JobEstimate("job-1", "Backend Lead", 10, 7, 3, false)));
	}

	@Test
	void theMatchingCardShowsTheJobsTheTopNTheEstimateAndWhatIsLeft() throws Exception {
		givenOpenJobWithSevenNewReports();
		when(usageMonitoringService.remaining(TENANT, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS)).thenReturn(60);

		var card = startScreening().preview(args("{\"jobIds\":[\"job-1\"]}"), CTX);

		assertThat(card.ok()).isTrue();
		// Only the new reports are charged; the unchanged ones are reused for free.
		assertThat(map(card.data())).containsEntry("estimatedActions", 7).containsEntry("reusedReports", 3)
			.containsEntry("remainingActions", 60);
		assertThat(map(card.data()).get("jobs").toString()).contains("topN=10");
	}

	@Test
	void closedJobsUnknownJobsAndNotEnoughQuotaAreRefused() throws Exception {
		var tool = startScreening();
		givenOpenJobWithSevenNewReports();
		when(jobPostService.findOneById("closed")).thenReturn(job("closed", "closed"));
		when(jobPostService.findOneById("foreign")).thenThrow(QorvaErrors.notFound(QorvaErrorCodes.AGENT_RUN_NOT_FOUND));

		assertThat(tool.preview(args("{\"jobIds\":[\"closed\"]}"), CTX).ok()).isFalse();
		assertThat(tool.preview(args("{\"jobIds\":[\"foreign\"]}"), CTX).ok()).isFalse();
		when(usageMonitoringService.remaining(TENANT, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS)).thenReturn(5);
		assertThat(tool.preview(args("{\"jobIds\":[\"job-1\"]}"), CTX).ok()).isFalse();
	}

	@Test
	void aTopNOffTheStepsOrAboveThePlanIsRefused() throws Exception {
		givenOpenJobWithSevenNewReports();
		when(usageMonitoringService.remaining(TENANT, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS)).thenReturn(null);

		assertThat(startScreening().preview(args("{\"jobIds\":[\"job-1\"],\"topN\":25}"), CTX).ok()).isFalse();
		assertThat(startScreening().preview(args("{\"jobIds\":[\"job-1\"],\"topN\":12}"), CTX).ok()).isFalse();
		assertThat(startScreening().preview(args("{\"jobIds\":[\"job-1\"],\"topN\":20}"), CTX).ok()).isTrue();
	}

	@Test
	void approvedMatchingScreensOnlyTheChosenJobsAtTheChosenTopN() throws Exception {
		when(screeningService.screenJobs(TENANT, List.of("job-1", "job-2"), 15, "en")).thenReturn(List.of(job("job-1", "open")));

		var result = startScreening()
			.execute(args("{\"jobIds\":[\"job-1\",\"job-2\",\"job-1\"],\"topN\":15}"), CTX, AgentApproval.UNCHANGED);

		verify(screeningService).screenJobs(TENANT, List.of("job-1", "job-2"), 15, "en");
		assertThat(result.summaryParams()).containsEntry("count", "1");
	}

	// ---- trigger_ats_sync ------------------------------------------------------------------

	@Test
	void anAtsImportIsProposedOnlyForAConnectedConnectionAndStartedAsTheUser() throws Exception {
		var connected = new AtsConnection();
		connected.setId("conn-1");
		connected.setProvider("GREENHOUSE");
		connected.setStatus(AtsConnection.STATUS_CONNECTED);
		var broken = new AtsConnection();
		broken.setId("conn-2");
		broken.setStatus("ERROR");
		when(connectionService.findOwned(TENANT, "conn-1")).thenReturn(connected);
		when(connectionService.findOwned(TENANT, "conn-2")).thenReturn(broken);
		var tool = new TriggerAtsSyncTool(connectionService, syncService);

		assertThat(tool.preview(args("{\"connectionId\":\"conn-1\"}"), CTX).ok()).isTrue();
		assertThat(tool.preview(args("{\"connectionId\":\"conn-2\"}"), CTX).ok()).isFalse();

		var queued = BackgroundJob.builder().id("sync-9").type(BackgroundJob.TYPE_ATS_SYNC).status(BackgroundJob.STATUS_PENDING).build();
		when(syncService.enqueue(eq(TENANT), eq("conn-1"), anyString(), eq("owner@a.test"))).thenReturn(BackgroundJobData.JobView.from(queued));

		var result = tool.execute(args("{\"connectionId\":\"conn-1\"}"), CTX, AgentApproval.UNCHANGED);

		verify(syncService).enqueue(TENANT, "conn-1", AtsSyncService.TRIGGER_MANUAL, "owner@a.test");
		assertThat(map(result.data())).containsEntry("syncJobId", "sync-9").containsEntry("started", true);
	}
}
