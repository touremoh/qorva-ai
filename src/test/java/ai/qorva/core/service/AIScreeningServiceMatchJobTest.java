package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.service.MatchingReportService.ReportState;
import ai.qorva.core.service.ats.AtsWriteBackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * One job's matching run: unchanged reports are reused for free, changed and new ones are generated and
 * metered, reports that left the top N are retired, and the run (Top N, cutoff) is recorded on the job.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AIScreeningServiceMatchJobTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";
	private static final String VERSION = "model-v1";
	/** Runs candidates inline so the test is deterministic. */
	private static final Executor INLINE = Runnable::run;

	@Mock private CVService cvService;
	@Mock private OpenAIService openAIService;
	@Mock private MatchingReportService reportService;
	@Mock private JobPostService jobPostService;
	@Mock private UsageMonitoringService usageMonitoringService;
	@Mock private AtsWriteBackService atsWriteBackService;
	@Mock private MatchingTopNPolicy topNPolicy;

	private AIScreeningService service;
	private JobPostDTO job;

	@BeforeEach
	void setUp() throws Exception {
		service = new AIScreeningService(cvService, openAIService, reportService, jobPostService,
			usageMonitoringService, atsWriteBackService, topNPolicy);
		job = new JobPostDTO();
		job.setId("job-1");
		job.setTenantId(TENANT);
		job.setTitle("Backend Lead");
		job.setDescription("Java");
		job.setStatus("open");
		job.setEmbedding(new float[] {1f, 0f});
		when(openAIService.reportVersion()).thenReturn(VERSION);
		when(openAIService.generateReport(anyString(), anyString(), anyString(), any())).thenReturn(new MatchingReportDetails());
		when(cvService.stillEligible(any(), any())).thenReturn(Set.of());
	}

	private static CVDTO cv(String id, String summary) {
		var cv = new CVDTO();
		cv.setId(id);
		cv.setCandidateProfileSummary(summary);
		return cv;
	}

	private String inputOf(CVDTO cv) {
		return MatchingFingerprint.input(MatchingFingerprint.cv(cv), MatchingFingerprint.job(job), "en", VERSION);
	}

	@Test
	void unchangedReportsAreReusedChangedAndNewOnesAreGeneratedAndOnlyThoseAreCharged() throws Exception {
		var unchanged = cv("cv-1", "same");
		var changed = cv("cv-2", "edited");
		var fresh = cv("cv-3", "new");
		when(cvService.match(job, 10)).thenReturn(List.of(
			new CVService.ScoredCv(unchanged, 0.91), new CVService.ScoredCv(changed, 0.84), new CVService.ScoredCv(fresh, 0.77)));
		var changedState = new ReportState("r-2", "cv-2", "stale-input", "stale-cv", 64.0, false);
		when(reportService.statesForJob(TENANT, "job-1")).thenReturn(Map.of(
			"cv-1", new ReportState("r-1", "cv-1", inputOf(unchanged), "x", 82.0, false),
			"cv-2", changedState));

		var outcome = service.matchJob(job, 10, "en", INLINE, AIScreeningService.Progress.NONE);

		assertThat(outcome.reused()).isEqualTo(1);
		assertThat(outcome.generated()).isEqualTo(2);
		verify(openAIService, times(2)).generateReport(anyString(), anyString(), eq("en"), any());
		verify(usageMonitoringService, times(2)).incrementUsage(TENANT, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, 1);
		verify(reportService).saveGenerated(eq(job), any(), eq(changed), eq(inputOf(changed)), anyString(), eq(changedState));
		verify(reportService).saveGenerated(eq(job), any(), eq(fresh), eq(inputOf(fresh)), anyString(), isNull());
		verify(reportService, never()).saveGenerated(any(), any(), eq(unchanged), any(), any(), any());
		verify(atsWriteBackService, times(2)).maybeEnqueue(any(), eq(job), any());
	}

	@Test
	void reportsOfCandidatesWhoLeftTheTopNAreRetired() throws Exception {
		when(cvService.match(job, 10)).thenReturn(List.of(new CVService.ScoredCv(cv("cv-1", "a"), 0.9)));
		when(reportService.statesForJob(TENANT, "job-1")).thenReturn(Map.of());

		service.matchJob(job, 10, "en", INLINE, AIScreeningService.Progress.NONE);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Set<String>> kept = ArgumentCaptor.forClass(Set.class);
		verify(reportService).markOutdated(eq(TENANT), eq("job-1"), kept.capture(), any());
		assertThat(kept.getValue()).containsExactly("cv-1");
	}

	@Test
	void aReusedOutdatedReportComesBackIntoTheResults() throws Exception {
		var back = cv("cv-1", "same");
		when(cvService.match(job, 10)).thenReturn(List.of(new CVService.ScoredCv(back, 0.9)));
		when(reportService.statesForJob(TENANT, "job-1")).thenReturn(Map.of(
			"cv-1", new ReportState("r-1", "cv-1", inputOf(back), "x", 70.0, true)));

		service.matchJob(job, 10, "en", INLINE, AIScreeningService.Progress.NONE);

		verify(reportService).markCurrent(TENANT, "r-1");
		verifyNoInteractions(atsWriteBackService);
	}

	@Test
	void theCutoffIsTheLastResultWhenTheTopNIsFullAndTheFloorOtherwise() throws Exception {
		when(reportService.statesForJob(TENANT, "job-1")).thenReturn(Map.of());
		when(cvService.match(job, 2)).thenReturn(List.of(
			new CVService.ScoredCv(cv("cv-1", "a"), 0.9), new CVService.ScoredCv(cv("cv-2", "b"), 0.8)));
		when(cvService.match(job, 5)).thenReturn(List.of(new CVService.ScoredCv(cv("cv-1", "a"), 0.9)));

		service.matchJob(job, 2, "en", INLINE, AIScreeningService.Progress.NONE);
		service.matchJob(job, 5, "en", INLINE, AIScreeningService.Progress.NONE);

		verify(jobPostService).recordRun(TENANT, "job-1", 2, 0.8, true);
		verify(jobPostService).recordRun(TENANT, "job-1", 5, 0.5, true);
	}

	@Test
	void aFailedCandidateLeavesTheJobFlaggedForAReRun() throws Exception {
		when(reportService.statesForJob(TENANT, "job-1")).thenReturn(Map.of());
		when(cvService.match(job, 10)).thenReturn(List.of(new CVService.ScoredCv(cv("cv-1", "a"), 0.9)));
		when(openAIService.generateReport(anyString(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("model down"));
		var outcomes = new ArrayList<AIScreeningService.CandidateOutcome>();

		var outcome = service.matchJob(job, 10, "en", INLINE, outcomes::add);

		assertThat(outcome.failed()).isEqualTo(1);
		assertThat(outcomes).containsExactly(AIScreeningService.CandidateOutcome.FAILED);
		verify(jobPostService).recordRun(TENANT, "job-1", 10, 0.5, false);
	}

	@Test
	void aJobAtlasHasNotEmbeddedYetIsSkippedUntouched() throws Exception {
		job.setEmbedding(null);

		var outcome = service.matchJob(job, 10, "en", INLINE, AIScreeningService.Progress.NONE);

		assertThat(outcome.skipped()).isTrue();
		verifyNoInteractions(cvService, reportService);
		verify(jobPostService, never()).recordRun(anyString(), anyString(), anyInt(), anyDouble(), anyBoolean());
	}
}
