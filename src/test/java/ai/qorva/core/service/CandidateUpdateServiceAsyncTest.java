package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CandidateUpdateRequest;
import ai.qorva.core.dao.repository.CandidateUpdateRequestRepository;
import ai.qorva.core.dao.repository.SuppressedEmailRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CandidateUpdateData;
import ai.qorva.core.enums.ContentDateSourceEnum;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Async-submission state machine: enqueue, status polling, retry semantics, finalize. */
@ExtendWith(MockitoExtension.class)
class CandidateUpdateServiceAsyncTest {

	private static final String TOKEN = "test-token";
	private static final String TENANT = "tenant-1";

	@Mock
	private CandidateUpdateRequestRepository requestRepository;

	@Mock
	private SuppressedEmailRepository suppressedEmailRepository;

	@Mock
	private CVService cvService;

	@Mock
	private LibraryQualityCacheEvictor cacheEvictor;

	@Mock
	private S3StorageService s3StorageService;

	private CandidateUpdateService service;

	@BeforeEach
	void setUp() {
		service = new CandidateUpdateService(requestRepository, suppressedEmailRepository,
			cvService, cacheEvictor, s3StorageService, new ObjectMapper());
	}

	private CandidateUpdateRequest request(String status) {
		return CandidateUpdateRequest.builder()
			.id("req-1")
			.tenantId(TENANT)
			.cvId("cv-old")
			.candidateEmail("jane@example.com")
			.tokenHash(CandidateUpdateService.sha256(TOKEN))
			.status(status)
			.expiresAt(Instant.now().plusSeconds(3600))
			.build();
	}

	private void stubLookup(CandidateUpdateRequest req) {
		when(requestRepository.findByTokenHash(CandidateUpdateService.sha256(TOKEN)))
			.thenReturn(Optional.of(req));
	}

	private MockMultipartFile pdf() {
		return new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[]{1, 2, 3});
	}

	@Test
	void enqueue_valid_stagesFileAndMarksSubmitted() throws Exception {
		var req = request(CandidateUpdateRequest.STATUS_OPENED);
		stubLookup(req);
		when(s3StorageService.uploadCandidateSubmission(eq(TENANT), eq("req-1"), any()))
			.thenReturn("candidate-submissions/tenant-1/req-1");

		service.enqueue(TOKEN, new CandidateUpdateData.Submission("activelyLooking", null, null, true, "EUR", 50000, 60000), pdf());

		var captor = ArgumentCaptor.forClass(CandidateUpdateRequest.class);
		verify(requestRepository).save(captor.capture());
		var saved = captor.getValue();
		assertThat(saved.getStatus()).isEqualTo(CandidateUpdateRequest.STATUS_SUBMITTED);
		assertThat(saved.getPendingFileKey()).isEqualTo("candidate-submissions/tenant-1/req-1");
		assertThat(saved.getOriginalFileName()).isEqualTo("resume.pdf");
		assertThat(saved.getSubmissionPayload()).contains("activelyLooking").contains("EUR");
		assertThat(saved.getSubmittedAt()).isNotNull();
	}

	@Test
	void enqueue_unsupportedFileType_isRejectedBeforeStaging() {
		stubLookup(request(CandidateUpdateRequest.STATUS_OPENED));
		var exe = new MockMultipartFile("file", "resume.exe", "application/octet-stream", new byte[]{1});

		assertThatThrownBy(() -> service.enqueue(TOKEN, null, exe))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("Unsupported file type");
		verify(requestRepository, never()).save(any());
	}

	@Test
	void enqueue_whileAlreadySubmitted_behavesAsNotFound() {
		stubLookup(request(CandidateUpdateRequest.STATUS_SUBMITTED));

		assertThatThrownBy(() -> service.enqueue(TOKEN, null, pdf()))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("no longer valid");
	}

	@Test
	void enqueue_afterFailure_isAllowedAgain() throws Exception {
		var req = request(CandidateUpdateRequest.STATUS_SUBMIT_FAILED);
		stubLookup(req);
		when(s3StorageService.uploadCandidateSubmission(anyString(), anyString(), any())).thenReturn("key");

		service.enqueue(TOKEN, null, pdf());

		verify(requestRepository).save(any());
		assertThat(req.getStatus()).isEqualTo(CandidateUpdateRequest.STATUS_SUBMITTED);
	}

	@Test
	void status_mapsLifecycleToPublicStates() throws Exception {
		var processing = request(CandidateUpdateRequest.STATUS_PROCESSING);
		processing.setProcessingStage(CandidateUpdateRequest.STAGE_UPDATING);

		stubLookup(request(CandidateUpdateRequest.STATUS_SUBMITTED));
		assertThat(service.status(TOKEN).state()).isEqualTo("SUBMITTED");

		stubLookup(processing);
		assertThat(service.status(TOKEN).state()).isEqualTo("UPDATING");

		processing.setProcessingStage(null);
		assertThat(service.status(TOKEN).state()).isEqualTo("PARSING");

		stubLookup(request(CandidateUpdateRequest.STATUS_COMPLETED));
		assertThat(service.status(TOKEN).state()).isEqualTo("DONE");

		stubLookup(request(CandidateUpdateRequest.STATUS_SUBMIT_FAILED));
		assertThat(service.status(TOKEN).state()).isEqualTo("FAILED");
	}

	@Test
	void status_beforeAnySubmission_behavesAsNotFound() {
		stubLookup(request(CandidateUpdateRequest.STATUS_OPENED));

		assertThatThrownBy(() -> service.status(TOKEN))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining("no longer valid");
	}

	@Test
	void finalizeStagedSubmission_replacesCvAppliesFieldsAndCompletes() throws Exception {
		var req = request(CandidateUpdateRequest.STATUS_PROCESSING);
		req.setPendingFileKey("candidate-submissions/tenant-1/req-1");
		req.setSubmissionPayload("{\"availabilityStatus\":\"notAvailable\"}");
		when(cvService.findOneById("cv-new")).thenReturn(new CVDTO());

		service.finalizeStagedSubmission(req, "cv-new");

		verify(cvService).replaceDuplicate("cv-new", "cv-old", TENANT);
		var cvCaptor = ArgumentCaptor.forClass(CVDTO.class);
		verify(cvService).updateOne(eq("cv-new"), cvCaptor.capture());
		assertThat(cvCaptor.getValue().getContentDateSource()).isEqualTo(ContentDateSourceEnum.VERIFIED.name());
		assertThat(cvCaptor.getValue().getPersonalInformation().getAvailability().getStatus()).isEqualTo("notAvailable");

		assertThat(req.getStatus()).isEqualTo(CandidateUpdateRequest.STATUS_COMPLETED);
		assertThat(req.getPendingFileKey()).isNull();
		verify(requestRepository).save(req);
		verify(cacheEvictor).evict(TENANT);
	}
}
