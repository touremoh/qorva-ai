package ai.qorva.core.service;

import ai.qorva.core.dao.repository.BackgroundJobRepository;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.CandidateUpdateRequestRepository;
import ai.qorva.core.dao.repository.ChatMessagesRepository;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.InsightConversationTurnRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dao.repository.QualityIssueStateRepository;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibraryClearServiceTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";

	@Mock private CVRepository cvRepository;
	@Mock private MatchingReportRepository matchingReportRepository;
	@Mock private ChatsRepository chatsRepository;
	@Mock private ChatMessagesRepository chatMessagesRepository;
	@Mock private InsightConversationTurnRepository insightConversationTurnRepository;
	@Mock private CandidateUpdateRequestRepository candidateUpdateRequestRepository;
	@Mock private QualityIssueStateRepository qualityIssueStateRepository;
	@Mock private BackgroundJobRepository backgroundJobRepository;
	@Mock private S3StorageService s3StorageService;
	@Mock private LibraryQualityCacheEvictor cacheEvictor;

	private LibraryClearService service;

	@BeforeEach
	void setUp() {
		service = new LibraryClearService(cvRepository, matchingReportRepository, chatsRepository,
			chatMessagesRepository, insightConversationTurnRepository, candidateUpdateRequestRepository,
			qualityIssueStateRepository, backgroundJobRepository, s3StorageService, cacheEvictor);
	}

	@Test
	void preflightReportsCounts() {
		when(cvRepository.countByTenantId(TENANT)).thenReturn(1204L);
		when(matchingReportRepository.countByTenantId(TENANT)).thenReturn(3410L);
		when(chatsRepository.countByTenantId(TENANT)).thenReturn(89L);

		var preflight = service.preflight(TENANT);

		assertThat(preflight.cvs()).isEqualTo(1204);
		assertThat(preflight.reports()).isEqualTo(3410);
		assertThat(preflight.chats()).isEqualTo(89);
	}

	@Test
	void clearRefusesWhileABackgroundJobIsActive() {
		when(backgroundJobRepository.existsByTenantIdAndTypeAndStatusIn(eq(TENANT), anyString(), anyList()))
			.thenReturn(true);

		assertThatThrownBy(() -> service.clear(TENANT, "user@qorva.ai"))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining(QorvaErrorCodes.CV_CLEAR_BLOCKED_BY_ACTIVE_JOB);
		verify(cvRepository, never()).deleteByTenantId(anyString());
		verify(s3StorageService, never()).deleteCvDocumentsForTenant(anyString());
	}

	@Test
	void clearWipesLibraryAndDerivedDataButNotJobsOrUsage() throws QorvaException {
		when(backgroundJobRepository.existsByTenantIdAndTypeAndStatusIn(eq(TENANT), anyString(), anyList()))
			.thenReturn(false);
		when(cvRepository.deleteByTenantId(TENANT)).thenReturn(1204L);
		when(matchingReportRepository.deleteByTenantId(TENANT)).thenReturn(3410L);
		when(chatsRepository.deleteByTenantId(TENANT)).thenReturn(89L);
		when(chatMessagesRepository.deleteByTenantId(TENANT)).thenReturn(640L);
		when(insightConversationTurnRepository.deleteByTenantId(TENANT)).thenReturn(12L);
		when(candidateUpdateRequestRepository.deleteByTenantId(TENANT)).thenReturn(7L);
		when(qualityIssueStateRepository.deleteByTenantId(TENANT)).thenReturn(3L);

		var result = service.clear(TENANT, "user@qorva.ai");

		assertThat(result.cvs()).isEqualTo(1204);
		assertThat(result.reports()).isEqualTo(3410);
		assertThat(result.chats()).isEqualTo(89);
		assertThat(result.chatMessages()).isEqualTo(640);
		assertThat(result.insightTurns()).isEqualTo(12);
		verify(s3StorageService).deleteCvDocumentsForTenant(TENANT);
		verify(s3StorageService).deleteCandidateSubmissionsForTenant(TENANT);
		verify(cacheEvictor).evict(TENANT);
	}
}
