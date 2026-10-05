package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.ConversationFrame;
import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.InsightResponseDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.MentionDTO;
import ai.qorva.core.dto.common.PersonalInformation;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.LibraryInsightsService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.agent.AgentRunStore;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.orchestrators.CandidateAnswerEngine;
import ai.qorva.core.service.orchestrators.ConversationTurn;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnswerToolsTest {

	private static final String TENANT = "64b7f0f0f0f0f0f0f0f0f0f0";
	private static final AgentToolContext CTX = new AgentToolContext(TENANT, "owner@a.test", "fr", null, "run-2");
	private static final AgentToolContext RULE_CTX = new AgentToolContext(TENANT, "owner@a.test", "fr", null, "run-2",
		AgentRun.ORIGIN_RULE, null);
	private static final ObjectMapper JSON = new ObjectMapper();

	@Mock private CandidateAnswerEngine engine;
	@Mock private LibraryInsightsService insightsService;
	@Mock private AgentRunStore store;
	@Mock private CVService cvService;
	@Mock private JobPostService jobPostService;
	@Mock private UsageMonitoringService usageMonitoringService;

	private AskAboutCandidateTool askAboutCandidate;
	private AnalyzeLibraryTool analyzeLibrary;
	private AgentRun run;
	private AgentRun earlier;

	@BeforeEach
	void setUp() throws QorvaException {
		askAboutCandidate = new AskAboutCandidateTool(engine, store, cvService, jobPostService, usageMonitoringService);
		analyzeLibrary = new AnalyzeLibraryTool(insightsService, store, usageMonitoringService);

		earlier = new AgentRun();
		earlier.setGoal("Who is Ana?");
		earlier.setFinalAnswer("A Java developer.");
		run = new AgentRun();
		run.setId("run-2");
		run.setGoal("Est-elle adaptée au poste ?");
		when(store.find(TENANT, "run-2")).thenReturn(Optional.of(run));
		when(store.earlierInConversation(run)).thenReturn(List.of(earlier));
		when(usageMonitoringService.hasCapacityFor(any(), any(), anyInt())).thenReturn(true);

		var cv = new CVDTO();
		cv.setId("cv-1");
		cv.setPersonalInformation(new PersonalInformation("Ana", null, null, null, null));
		when(cvService.findOneById("cv-1")).thenReturn(cv);
		var job = new JobPostDTO();
		job.setId("job-1");
		job.setTitle("Backend engineer");
		when(jobPostService.findOneById("job-1")).thenReturn(job);
	}

	@Test
	void askAboutCandidatePassesTheRecruitersOwnQuestionAndTheFocus() throws Exception {
		run.setFocus(new AgentRun.Focus("cv-1", "Ana", "job-1", "Backend engineer"));
		when(engine.answer(eq(TENANT), eq("cv-1"), eq("job-1"), eq("French"), anyList(), eq("Est-elle adaptée au poste ?")))
			.thenReturn(new CandidateAnswerEngine.Answer("Oui : le rapport la note à 64 %.", "rep-1", 64.0));

		var result = askAboutCandidate.execute(JSON.createObjectNode(), CTX);

		assertThat(result.ok()).isTrue();
		assertThat(result.answer().text()).isEqualTo("Oui : le rapport la note à 64 %.");
		assertThat(result.answer().blocks()).isNull();
		assertThat(result.links()).extracting(AgentRun.Link::getType).containsExactly("CV", "JOB", "REPORT");
		assertThat(askAboutCandidate.terminal()).isTrue();
	}

	@Test
	void askAboutCandidateReplaysTheConversationsEarlierExchanges() {
		assertThat(AskAboutCandidateTool.earlierTurns(List.of(earlier)))
			.containsExactly(ConversationTurn.recruiter("Who is Ana?"), ConversationTurn.assistant("A Java developer."));
	}

	@Test
	void askAboutCandidateFallsBackToTheOnlyMentionedRecords() throws Exception {
		run.setMentions(new ArrayList<>(List.of(new AgentRun.Mention("CV", "cv-1", "Ana"), new AgentRun.Mention("JOB", "job-1", "Backend engineer"))));
		when(engine.answer(any(), eq("cv-1"), eq("job-1"), any(), anyList(), any()))
			.thenReturn(new CandidateAnswerEngine.Answer("Yes.", null, null));

		var result = askAboutCandidate.execute(JSON.createObjectNode(), CTX);

		assertThat(result.ok()).isTrue();
		assertThat(result.links()).extracting(AgentRun.Link::getType).containsExactly("CV", "JOB");
	}

	@Test
	void askAboutCandidateNeedsAJob() throws Exception {
		var result = askAboutCandidate.execute(JSON.readTree("{\"cvId\":\"cv-1\"}"), CTX);

		assertThat(result.ok()).isFalse();
		assertThat(result.error()).isEqualTo(AskAboutCandidateTool.JOB_REQUIRED);
		verify(engine, never()).answer(any(), any(), any(), any(), anyList(), any());
	}

	@Test
	void askAboutCandidateRefusesWhenTheCandidateQuestionsAreUsedUp() throws Exception {
		when(usageMonitoringService.hasCapacityFor(TENANT, UsageMonitoringService.FeatureKey.AI_RESUME_CHATS, 1)).thenReturn(false);

		var result = askAboutCandidate.execute(JSON.readTree("{\"cvId\":\"cv-1\",\"jobId\":\"job-1\"}"), CTX);

		assertThat(result.error()).isEqualTo(AskAboutCandidateTool.LIMIT_REACHED);
		verify(engine, never()).answer(any(), any(), any(), any(), anyList(), any());
	}

	@Test
	void askAboutCandidateTellsCopilotATimedOutAnswerWasTooLong() throws Exception {
		when(engine.answer(any(), any(), any(), any(), anyList(), any()))
			.thenThrow(new QorvaException(QorvaErrorCodes.AI_ANSWER_TOO_LONG));

		var result = askAboutCandidate.execute(JSON.readTree("{\"cvId\":\"cv-1\",\"jobId\":\"job-1\"}"), CTX);

		assertThat(result.ok()).isFalse();
		assertThat(result.error()).isEqualTo(AskAboutCandidateTool.TOO_LONG);
	}

	@Test
	void answerToolsAreForChatsOnly() {
		assertThat(askAboutCandidate.available(CTX)).isTrue();
		assertThat(askAboutCandidate.available(RULE_CTX)).isFalse();
		assertThat(analyzeLibrary.available(RULE_CTX)).isFalse();
	}

	@Test
	void analyzeLibraryReturnsTheHandlersBlocksAndCarriesTheFrame() {
		var previous = new ConversationFrame("top 10 profiles", InsightIntent.CANDIDATE_RANKING, null, false, null);
		earlier.setInsightFrame(previous);
		run.setMentions(new ArrayList<>(List.of(new AgentRun.Mention("JOB", "job-1", "Backend engineer"))));
		var response = new InsightResponseDTO(null, InsightIntent.CANDIDATE_RANKING, "Here are 10 Java profiles.", List.of(), 10,
			List.of(), List.of(), List.of(), null, Map.of("k", "v"));
		var next = new ConversationFrame("top 10 java profiles", InsightIntent.CANDIDATE_RANKING, null, false, null);
		when(insightsService.analyse(eq("Est-elle adaptée au poste ?"),
			eq(List.of(new MentionDTO(MentionDTO.TYPE_JOB, "job-1", "Backend engineer"))), eq(TENANT), eq(previous)))
			.thenReturn(new LibraryInsightsService.Analysis(response, next, false));

		var result = analyzeLibrary.execute(JSON.createObjectNode(), CTX);

		assertThat(result.ok()).isTrue();
		assertThat(result.answer().text()).isEqualTo("Here are 10 Java profiles.");
		assertThat(result.answer().blocks().totalCandidateCount()).isEqualTo(10);
		assertThat(result.answer().blocks().rawData()).containsEntry("k", "v");
		assertThat(result.answer().insightFrame()).isEqualTo(next);
	}

	@Test
	void analyzeLibraryRefusesWhenTheAnalysesAreUsedUp() {
		when(usageMonitoringService.hasCapacityFor(TENANT, UsageMonitoringService.FeatureKey.TALENT_INTELLIGENCE_QUERIES, 1))
			.thenReturn(false);

		var result = analyzeLibrary.execute(JSON.createObjectNode(), CTX);

		assertThat(result.error()).isEqualTo(AnalyzeLibraryTool.LIMIT_REACHED);
		verify(insightsService, never()).analyse(anyString(), anyList(), anyString(), any());
	}

	@Test
	void onlyThePreviousExchangesFrameCarriesOver() {
		var older = new AgentRun();
		older.setInsightFrame(new ConversationFrame("q", InsightIntent.SKILLS_DISTRIBUTION, null, false, null));
		assertThat(AnalyzeLibraryTool.previousFrame(List.of(older, earlier))).isNull();
		assertThat(AnalyzeLibraryTool.previousFrame(List.of())).isNull();
	}
}
