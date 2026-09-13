package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.common.ChatContext;
import ai.qorva.core.dto.common.DecisionSummary;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.MatchingReportMapper;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningContextProviderImplTest {

	private static final String TENANT = new ObjectId().toHexString();
	private static final String JOB = new ObjectId().toHexString();
	private static final String CV = new ObjectId().toHexString();
	private static final String REPORT = new ObjectId().toHexString();

	@Mock private CVService cvService;
	@Mock private JobPostService jobPostService;
	@Mock private MatchingReportRepository matchingReportRepository;
	@Mock private MatchingReportMapper matchingReportMapper;

	private ScreeningContextProviderImpl provider;

	@BeforeEach
	void setUp() throws QorvaException {
		provider = new ScreeningContextProviderImpl(cvService, jobPostService, matchingReportRepository, matchingReportMapper, new ChatContextSerializer());
		when(cvService.findOneById(CV)).thenReturn(CVDTO.builder().candidateProfileSummary("Java dev").lastUpdatedAt(Instant.parse("2026-09-01T00:00:00Z")).build());
		when(jobPostService.findOneById(JOB)).thenReturn(JobPostDTO.builder().title("Backend").build());
	}

	private static Chat chat(String reportId) {
		return Chat.builder().id("c1").tenantId(TENANT)
			.context(ChatContext.builder().cvId(CV).jobPostId(JOB).matchingReportId(reportId).build()).build();
	}

	private static MatchingReportDTO reportDto(double score, Instant updatedAt) {
		var summary = new DecisionSummary();
		summary.setFinalScore(score);
		var details = new MatchingReportDetails();
		details.setDecisionSummary(summary);
		return MatchingReportDTO.builder().id(REPORT).tenantId(TENANT).matchingReportDetails(details).lastUpdatedAt(updatedAt).build();
	}

	@Test
	void findsAReportGeneratedAfterTheChatWasCreated() throws QorvaException {
		var entity = new MatchingReport();
		when(matchingReportRepository.findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(new ObjectId(TENANT), new ObjectId(JOB), CV))
			.thenReturn(Optional.of(entity));
		when(matchingReportMapper.map(entity)).thenReturn(reportDto(64.0, Instant.parse("2026-09-10T00:00:00Z")));

		var ctx = provider.load(chat(null));

		assertThat(ctx.hasReport()).isTrue();
		assertThat(ctx.matchingReportId()).isEqualTo(REPORT);
		assertThat(ctx.finalScore()).isEqualTo(64.0);
		assertThat(ctx.reportStale()).isFalse();
		assertThat(ctx.cvText()).contains("Java dev");
		assertThat(ctx.jobText()).contains("Backend");
		verify(matchingReportRepository, never()).findById(any());
	}

	@Test
	void reportsNothingWhenNoReportExists() throws QorvaException {
		when(matchingReportRepository.findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(any(), any(), any())).thenReturn(Optional.empty());

		var ctx = provider.load(chat(null));

		assertThat(ctx.hasReport()).isFalse();
		assertThat(ctx.matchingReportId()).isNull();
		assertThat(ctx.finalScore()).isNull();
	}

	@Test
	void loadsALinkedReportByIdAndFlagsItStaleWhenTheCvIsNewer() throws QorvaException {
		var entity = new MatchingReport();
		entity.setTenantId(TENANT);
		when(matchingReportRepository.findById(new ObjectId(REPORT))).thenReturn(Optional.of(entity));
		when(matchingReportMapper.map(entity)).thenReturn(reportDto(50.0, Instant.parse("2026-08-01T00:00:00Z")));

		var ctx = provider.load(chat(REPORT));

		assertThat(ctx.finalScore()).isEqualTo(50.0);
		assertThat(ctx.reportStale()).isTrue();
	}

	@Test
	void ignoresALinkedReportFromAnotherTenant() throws QorvaException {
		var entity = new MatchingReport();
		entity.setTenantId(new ObjectId().toHexString());
		when(matchingReportRepository.findById(new ObjectId(REPORT))).thenReturn(Optional.of(entity));

		var ctx = provider.load(chat(REPORT));

		assertThat(ctx.hasReport()).isFalse();
	}
}
