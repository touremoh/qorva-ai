package ai.qorva.core.service;

import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.repository.BackgroundJobRepository;
import ai.qorva.core.dto.ProductReferenceDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UsageMonitoringDTO;
import ai.qorva.core.dto.common.FeatureLimits;
import ai.qorva.core.dto.common.ProductFeatures;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.dto.common.UsageFeatureMetrics;
import ai.qorva.core.dto.common.UsageFeatures;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkCvUploadServiceTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";
	private static final String JOB_ID = "job-1";

	@Mock private BackgroundJobRepository jobRepository;
	@Mock private S3StorageService s3StorageService;
	@Mock private UsageMonitoringService usageMonitoringService;
	@Mock private TenantService tenantService;
	@Mock private ProductReferenceService productReferenceService;
	@Mock private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

	private BulkCvUploadService service;

	@BeforeEach
	void setUp() {
		service = new BulkCvUploadService(jobRepository, s3StorageService, usageMonitoringService,
			tenantService, productReferenceService, mongoTemplate);
	}

	private void givenPlanCap(Integer cap) throws QorvaException {
		var sub = new SubscriptionInfo();
		sub.setPriceId("price_123");
		var tenant = new TenantDTO();
		tenant.setSubscriptionInfo(sub);
		lenient().when(tenantService.findOneById(TENANT)).thenReturn(tenant);
		var product = new ProductReferenceDTO();
		product.setFeatures(ProductFeatures.builder()
			.limits(FeatureLimits.builder().bulkUploadFiles(cap).build())
			.build());
		lenient().when(productReferenceService.findByStripePriceId("price_123")).thenReturn(product);
	}

	private BackgroundJob draftJob(List<BackgroundJob.StagedFile> staged) {
		return BackgroundJob.builder()
			.id(JOB_ID)
			.tenantId(TENANT)
			.type(BackgroundJob.TYPE_BULK_CV_UPLOAD)
			.status(BackgroundJob.STATUS_DRAFT)
			.stagedFiles(staged)
			.errorSamples(List.of())
			.build();
	}

	private MultipartFile pdf(String name) {
		return new MockMultipartFile("files", name, "application/pdf", new byte[] {1, 2, 3});
	}

	// --- tier cap resolution -------------------------------------------------

	@Test
	void maxFilesResolvesFromPlan() throws QorvaException {
		givenPlanCap(300);
		assertThat(service.maxFilesForTenant(TENANT)).isEqualTo(300);
	}

	@Test
	void maxFilesFallsBackToStarterWhenPlanUnresolvable() throws QorvaException {
		when(tenantService.findOneById(TENANT)).thenThrow(new QorvaException("boom"));
		assertThat(service.maxFilesForTenant(TENANT)).isEqualTo(BulkCvUploadService.DEFAULT_MAX_FILES);
	}

	@Test
	void maxFilesFallsBackWhenLimitMissing() throws QorvaException {
		givenPlanCap(null);
		assertThat(service.maxFilesForTenant(TENANT)).isEqualTo(BulkCvUploadService.DEFAULT_MAX_FILES);
	}

	// --- create --------------------------------------------------------------

	@Test
	void createRejectsWhenActiveJobExists() {
		when(jobRepository.existsByTenantIdAndTypeAndStatusIn(eq(TENANT), eq(BackgroundJob.TYPE_BULK_CV_UPLOAD), anyList()))
			.thenReturn(true);
		assertThatThrownBy(() -> service.create(TENANT, "user@qorva.ai"))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining(QorvaErrorCodes.BULK_JOB_ACTIVE_EXISTS);
	}

	@Test
	void createReturnsJobIdAndPlanCap() throws QorvaException {
		givenPlanCap(1000);
		when(jobRepository.existsByTenantIdAndTypeAndStatusIn(anyString(), anyString(), anyList())).thenReturn(false);
		when(jobRepository.save(any())).thenAnswer(inv -> {
			BackgroundJob job = inv.getArgument(0);
			job.setId(JOB_ID);
			return job;
		});

		var resp = service.create(TENANT, "user@qorva.ai");

		assertThat(resp.jobId()).isEqualTo(JOB_ID);
		assertThat(resp.maxFiles()).isEqualTo(1000);
	}

	// --- staging -------------------------------------------------------------

	@Test
	void appendRollsBackStagedObjectsWhenOverPlanCap() throws QorvaException {
		givenPlanCap(100);
		// Job stays DRAFT on re-read, so the guarded update missing means the cap was hit.
		when(jobRepository.findByIdAndTenantId(JOB_ID, TENANT)).thenReturn(Optional.of(draftJob(List.of())));
		when(s3StorageService.uploadStagedCv(eq(TENANT), eq(JOB_ID), anyString(), any()))
			.thenAnswer(inv -> "staged-cv-uploads/" + TENANT + "/" + JOB_ID + "/" + inv.getArgument(2));
		when(mongoTemplate.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
			any(org.springframework.data.mongodb.core.query.Update.class), eq(BackgroundJob.class)))
			.thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(0, 0L, null));

		assertThatThrownBy(() -> service.appendFiles(TENANT, JOB_ID, List.of(pdf("a.pdf"), pdf("b.pdf"))))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining(QorvaErrorCodes.BULK_LIMIT_FOR_PLAN);
		// The two objects staged before the guarded update failed are cleaned up.
		verify(s3StorageService, times(2)).deleteObject(anyString());
	}

	@Test
	void appendRejectsWhenJobNotDraft() {
		var job = draftJob(List.of());
		job.setStatus(BackgroundJob.STATUS_RUNNING);
		when(jobRepository.findByIdAndTenantId(JOB_ID, TENANT)).thenReturn(Optional.of(job));

		assertThatThrownBy(() -> service.appendFiles(TENANT, JOB_ID, List.of(pdf("a.pdf"))))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining(QorvaErrorCodes.BULK_JOB_NOT_DRAFT);
	}

	@Test
	void appendStagesFilesToS3AndAppendsAtomically() throws QorvaException {
		givenPlanCap(100);
		var afterAppend = draftJob(List.of(
			BackgroundJob.StagedFile.builder().s3Key("k0").build(),
			BackgroundJob.StagedFile.builder().s3Key("k1").build()));
		when(jobRepository.findByIdAndTenantId(JOB_ID, TENANT))
			.thenReturn(Optional.of(draftJob(List.of())))
			.thenReturn(Optional.of(afterAppend));
		when(s3StorageService.uploadStagedCv(eq(TENANT), eq(JOB_ID), anyString(), any()))
			.thenAnswer(inv -> "staged-cv-uploads/" + TENANT + "/" + JOB_ID + "/" + inv.getArgument(2));
		when(mongoTemplate.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
			any(org.springframework.data.mongodb.core.query.Update.class), eq(BackgroundJob.class)))
			.thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));

		var resp = service.appendFiles(TENANT, JOB_ID, List.of(pdf("a.pdf"), pdf("b.pdf")));

		assertThat(resp.stagedCount()).isEqualTo(2);
		assertThat(resp.maxFiles()).isEqualTo(100);
		verify(s3StorageService, times(2)).uploadStagedCv(eq(TENANT), eq(JOB_ID), anyString(), any());
	}

	// --- start ---------------------------------------------------------------

	@Test
	void startRejectsEmptyJob() {
		when(jobRepository.findByIdAndTenantId(JOB_ID, TENANT)).thenReturn(Optional.of(draftJob(List.of())));
		assertThatThrownBy(() -> service.start(TENANT, JOB_ID))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining(QorvaErrorCodes.BULK_JOB_NO_FILES);
	}

	@Test
	void startBlocksWhenNoScreeningCapacityAtAll() {
		var staged = List.of(BackgroundJob.StagedFile.builder().s3Key("k0").build());
		when(jobRepository.findByIdAndTenantId(JOB_ID, TENANT)).thenReturn(Optional.of(draftJob(staged)));
		when(usageMonitoringService.hasCapacityFor(TENANT, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, 1))
			.thenReturn(false);

		assertThatThrownBy(() -> service.start(TENANT, JOB_ID))
			.isInstanceOf(QorvaException.class)
			.hasMessageContaining(QorvaErrorCodes.USAGE_SCREENING_LIMIT_EXCEEDED);
	}

	@Test
	void startReportsHowManyFilesFitTheRemainingQuota() throws QorvaException {
		var staged = new java.util.ArrayList<BackgroundJob.StagedFile>();
		for (int i = 0; i < 10; i++) {
			staged.add(BackgroundJob.StagedFile.builder().s3Key("k" + i).build());
		}
		when(jobRepository.findByIdAndTenantId(JOB_ID, TENANT)).thenReturn(Optional.of(draftJob(staged)));
		when(usageMonitoringService.hasCapacityFor(TENANT, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS, 1))
			.thenReturn(true);
		var usage = new UsageMonitoringDTO();
		usage.setFeatures(UsageFeatures.builder()
			.screeningActions(UsageFeatureMetrics.builder().limit(100).consumed(96).build())
			.build());
		when(usageMonitoringService.findCurrentPeriodByTenantId(TENANT)).thenReturn(Optional.of(usage));

		var resp = service.start(TENANT, JOB_ID);

		assertThat(resp.job().status()).isEqualTo(BackgroundJob.STATUS_PENDING);
		assertThat(resp.job().total()).isEqualTo(10);
		assertThat(resp.willProcess()).isEqualTo(4);
	}

	// --- cancel --------------------------------------------------------------

	@Test
	void cancelDeletesStagedObjects() throws QorvaException {
		var staged = List.of(
			BackgroundJob.StagedFile.builder().s3Key("k0").build(),
			BackgroundJob.StagedFile.builder().s3Key("k1").build());
		var job = draftJob(staged);
		when(jobRepository.findByIdAndTenantId(JOB_ID, TENANT)).thenReturn(Optional.of(job));

		var view = service.cancel(TENANT, JOB_ID);

		assertThat(view.status()).isEqualTo(BackgroundJob.STATUS_CANCELLED);
		verify(s3StorageService).deleteObject("k0");
		verify(s3StorageService).deleteObject("k1");
	}
}
