package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CandidateOutreachData;
import ai.qorva.core.dto.JobDescriptionData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.NoteDTO;
import ai.qorva.core.dto.NoteRequest;
import ai.qorva.core.dto.common.PersonalInformation;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.CandidateOutreachDraftService;
import ai.qorva.core.service.JobDescriptionBuilderService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.NoteService;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentToolContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WriteToolsTest {

	private static final String TENANT = "64b7f0f0f0f0f0f0f0f0f0f0";
	private static final AgentToolContext CTX = new AgentToolContext(TENANT, "owner@a.test", "fr", null);
	private static final ObjectMapper JSON = new ObjectMapper();

	@Mock private CVService cvService;
	@Mock private NoteService noteService;
	@Mock private JobPostService jobPostService;
	@Mock private JobDescriptionBuilderService descriptionBuilder;
	@Mock private CandidateOutreachDraftService draftService;

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object data) {
		return (Map<String, Object>) data;
	}

	private static JsonNode args(String json) throws Exception {
		return JSON.readTree(json);
	}

	private static CVDTO cv(String id, String name, String... tags) {
		var cv = new CVDTO();
		cv.setId(id);
		cv.setTenantId(TENANT);
		cv.setPersonalInformation(new PersonalInformation(name, null, null, null, null));
		cv.setTags(new ArrayList<>(List.of(tags)));
		return cv;
	}

	// ---- tags ------------------------------------------------------------------------------

	@Test
	void tagChangesCompareCaseInsensitivelyAndReportNoChange() {
		assertThat(CvTagChange.added(List.of("Java"), List.of("java", "shortlist"))).containsExactly("Java", "shortlist");
		assertThat(CvTagChange.added(List.of("Java"), List.of("JAVA"))).isNull();
		assertThat(CvTagChange.removed(List.of("Java", "shortlist"), List.of("SHORTLIST"))).containsExactly("Java");
		assertThat(CvTagChange.removed(List.of("Java"), List.of("go"))).isNull();
	}

	@Test
	void addTagsUpdatesOnlyTheCvsThatChangeAndSkipsForeignIds() throws Exception {
		when(cvService.findOneById("cv-1")).thenReturn(cv("cv-1", "Ana", "java"));
		when(cvService.findOneById("cv-2")).thenReturn(cv("cv-2", "Bruno", "shortlist"));
		when(cvService.findOneById("foreign")).thenThrow(QorvaErrors.notFound(QorvaErrorCodes.AGENT_RUN_NOT_FOUND));
		var tool = new AddCvTagsTool(cvService);

		var result = tool.execute(args("{\"cvIds\":[\"cv-1\",\"cv-2\",\"foreign\"],\"tags\":[\"Shortlist \"]}"), CTX);

		var patch = ArgumentCaptor.forClass(CVDTO.class);
		verify(cvService).updateOne(eq("cv-1"), patch.capture());
		verify(cvService, never()).updateOne(eq("cv-2"), any());
		assertThat(patch.getValue().getTags()).containsExactly("java", "Shortlist");
		assertThat(patch.getValue().getPersonalInformation()).as("a tags-only patch").isNull();
		assertThat(result.ok()).isTrue();
		assertThat(result.summaryParams()).containsEntry("count", "1").containsEntry("tags", "Shortlist");
		assertThat(asMap(result.data())).containsEntry("notFound", List.of("foreign"))
			.containsEntry("alreadyInThatState", List.of("cv-2"));
		assertThat(tool.tier()).isEqualTo(AgentRiskTier.WRITE_INTERNAL);
	}

	@Test
	void tagCallsAreBounded() throws Exception {
		var tool = new AddCvTagsTool(cvService);
		var ids = new StringBuilder();
		for (int i = 0; i < CvTagChange.MAX_CVS + 1; i++) ids.append(i == 0 ? "" : ",").append("\"cv-").append(i).append('"');

		assertThat(tool.execute(args("{\"cvIds\":[" + ids + "],\"tags\":[\"x\"]}"), CTX).ok()).isFalse();
		assertThat(tool.execute(args("{\"cvIds\":[\"cv-1\"],\"tags\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]}"), CTX).ok()).isFalse();
		assertThat(tool.execute(args("{\"cvIds\":[\"cv-1\"],\"tags\":[\"" + "x".repeat(41) + "\"]}"), CTX).ok()).isFalse();
		assertThat(tool.execute(args("{\"cvIds\":[],\"tags\":[\"x\"]}"), CTX).ok()).isFalse();
		verify(cvService, never()).updateOne(anyString(), any());
	}

	@Test
	void removeTagsLeavesOtherTags() throws Exception {
		when(cvService.findOneById("cv-1")).thenReturn(cv("cv-1", "Ana", "java", "shortlist"));

		new RemoveCvTagsTool(cvService).execute(args("{\"cvIds\":[\"cv-1\"],\"tags\":[\"SHORTLIST\"]}"), CTX);

		var patch = ArgumentCaptor.forClass(CVDTO.class);
		verify(cvService).updateOne(eq("cv-1"), patch.capture());
		assertThat(patch.getValue().getTags()).containsExactly("java");
	}

	// ---- notes -----------------------------------------------------------------------------

	@Test
	void aNoteIsWrittenAsTheUserOnTheResolvedCv() throws Exception {
		when(cvService.findOneById("cv-1")).thenReturn(cv("cv-1", "Ana"));
		var saved = new NoteDTO();
		saved.setId("note-1");
		when(noteService.create(eq(TENANT), eq("owner@a.test"), any())).thenReturn(saved);

		var result = new AddNoteTool(noteService, cvService).execute(args("{\"cvId\":\"cv-1\",\"text\":\"Strong on Kotlin.\"}"), CTX);

		var request = ArgumentCaptor.forClass(NoteRequest.class);
		verify(noteService).create(eq(TENANT), eq("owner@a.test"), request.capture());
		assertThat(request.getValue().getTargetType()).isEqualTo("CV");
		assertThat(request.getValue().getTargetId()).isEqualTo("cv-1");
		assertThat(result.summaryParams()).containsEntry("name", "Ana");
	}

	// ---- jobs ------------------------------------------------------------------------------

	@Test
	void aPlainTextDescriptionBecomesEscapedParagraphs() throws Exception {
		when(jobPostService.createOne(any())).thenAnswer(inv -> {
			var job = inv.<JobPostDTO>getArgument(0);
			job.setId("job-1");
			return job;
		});

		new CreateJobPostTool(jobPostService, descriptionBuilder)
			.execute(args("{\"title\":\"Backend Lead\",\"description\":\"Lead <the> team.\\n\\nJava & Kotlin.\"}"), CTX);

		var job = ArgumentCaptor.forClass(JobPostDTO.class);
		verify(jobPostService).createOne(job.capture());
		assertThat(job.getValue().getTenantId()).isEqualTo(TENANT);
		assertThat(job.getValue().getDescription()).isEqualTo("<p>Lead &lt;the&gt; team.</p><p>Java &amp; Kotlin.</p>");
		verify(descriptionBuilder, never()).generate(any(), any(), any());
	}

	@Test
	void aGeneratedDescriptionUsesTheBuilderInTheRunLanguage() throws Exception {
		when(descriptionBuilder.generate(eq(TENANT), any(), eq("fr")))
			.thenReturn(new JobDescriptionData.GenerateResponse("Backend Lead", "<p>Generated</p>", null));
		when(jobPostService.createOne(any())).thenAnswer(inv -> inv.getArgument(0));

		new CreateJobPostTool(jobPostService, descriptionBuilder)
			.execute(args("{\"title\":\"Backend Lead\",\"generateDescription\":true,\"mustHaveSkills\":\"Java\"}"), CTX);

		var request = ArgumentCaptor.forClass(JobDescriptionData.GenerateRequest.class);
		verify(descriptionBuilder).generate(eq(TENANT), request.capture(), eq("fr"));
		assertThat(request.getValue().getMustHaveSkills()).isEqualTo("Java");
		var job = ArgumentCaptor.forClass(JobPostDTO.class);
		verify(jobPostService).createOne(job.capture());
		assertThat(job.getValue().getDescription()).isEqualTo("<p>Generated</p>");
	}

	@Test
	void updatingAJobSendsOnlyTheChangedFields() throws Exception {
		var existing = new JobPostDTO();
		existing.setId("job-1");
		existing.setTitle("Backend Lead");
		when(jobPostService.findOneById("job-1")).thenReturn(existing);
		when(jobPostService.updateOne(eq("job-1"), any())).thenReturn(existing);
		var tool = new UpdateJobPostTool(jobPostService);

		tool.execute(args("{\"jobId\":\"job-1\",\"status\":\"closed\"}"), CTX);

		var patch = ArgumentCaptor.forClass(JobPostDTO.class);
		verify(jobPostService).updateOne(eq("job-1"), patch.capture());
		assertThat(patch.getValue().getStatus()).isEqualTo("closed");
		assertThat(patch.getValue().getTitle()).isNull();
		assertThat(patch.getValue().getDescription()).isNull();
		assertThat(tool.execute(args("{\"jobId\":\"job-1\",\"status\":\"deleted\"}"), CTX).ok()).isFalse();
		assertThat(tool.execute(args("{\"jobId\":\"job-1\"}"), CTX).ok()).isFalse();
	}

	// ---- outreach draft --------------------------------------------------------------------

	@Test
	void aDraftIsReturnedNeverSent() throws Exception {
		when(cvService.findOneById("cv-1")).thenReturn(cv("cv-1", "Ana"));
		when(draftService.draft(eq(TENANT), eq("owner@a.test"), any(), eq("fr")))
			.thenReturn(new CandidateOutreachData.DraftResponse("Bonjour", "Corps"));

		var result = new DraftOutreachTool(draftService, cvService)
			.execute(args("{\"cvId\":\"cv-1\",\"intent\":\"intro\",\"jobId\":\"job-1\"}"), CTX);

		var request = ArgumentCaptor.forClass(CandidateOutreachData.DraftRequest.class);
		verify(draftService).draft(eq(TENANT), eq("owner@a.test"), request.capture(), eq("fr"));
		assertThat(request.getValue().getIntent()).isEqualTo("INTRO");
		assertThat(request.getValue().getJobPostId()).isEqualTo("job-1");
		assertThat(asMap(result.data())).containsEntry("subject", "Bonjour").containsEntry("sent", false);
		assertThat(new DraftOutreachTool(draftService, cvService)
			.execute(args("{\"cvId\":\"cv-1\",\"intent\":\"spam\"}"), CTX).ok()).isFalse();
	}
}
