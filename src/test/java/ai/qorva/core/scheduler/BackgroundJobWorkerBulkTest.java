package ai.qorva.core.scheduler;

import ai.qorva.core.dao.entity.BackgroundJob;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.mapper.OpenAIResultMapper;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.CandidateUpdateEmailService;
import ai.qorva.core.service.CandidateUpdateService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.LibraryQualityCacheEvictor;
import ai.qorva.core.service.OpenAIService;
import ai.qorva.core.service.S3StorageService;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.service.UserService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundJobWorkerBulkTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";
	private static final String JOB_ID = "bulk-job-1";

	@Mock private MongoTemplate mongoTemplate;
	@Mock private CVRepository cvRepository;
	@Mock private CVService cvService;
	@Mock private OpenAIService openAIService;
	@Mock private OpenAIResultMapper openAIResultMapper;
	@Mock private UsageMonitoringService usageMonitoringService;
	@Mock private LibraryQualityCacheEvictor cacheEvictor;
	@Mock private CandidateUpdateService candidateUpdateService;
	@Mock private CandidateUpdateEmailService candidateUpdateEmailService;
	@Mock private TenantService tenantService;
	@Mock private UserService userService;
	@Mock private S3StorageService s3StorageService;
	@Mock private JobPostService jobPostService;

	private BackgroundJobWorker worker;

	@BeforeEach
	void setUp() {
		worker = new BackgroundJobWorker(mongoTemplate, cvRepository, cvService, openAIService,
			openAIResultMapper, usageMonitoringService, cacheEvictor, candidateUpdateService,
			candidateUpdateEmailService, tenantService, userService, s3StorageService, jobPostService);
	}

	private BackgroundJob bulkJob() {
		return BackgroundJob.builder()
			.id(JOB_ID)
			.tenantId(TENANT)
			.type(BackgroundJob.TYPE_BULK_CV_UPLOAD)
			.status(BackgroundJob.STATUS_RUNNING)
			.total(2)
			.errorSamples(List.of())
			.stagedFiles(List.of(
				BackgroundJob.StagedFile.builder().s3Key("s/0").filename("a.pdf").contentType("application/pdf").build(),
				BackgroundJob.StagedFile.builder().s3Key("s/1").filename("b.pdf").contentType("application/pdf").build()))
			.startedAt(Instant.now())
			.build();
	}

	private void givenClaimedJob(BackgroundJob job) {
		when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
			any(FindAndModifyOptions.class), eq(BackgroundJob.class))).thenReturn(job);
		// isCancelled() re-reads the job; keep it RUNNING throughout.
		lenient().when(mongoTemplate.findById(JOB_ID, BackgroundJob.class)).thenReturn(job);
	}

	private Document lastFinalUpdate() {
		var captor = ArgumentCaptor.forClass(Update.class);
		verify(mongoTemplate, atLeastOnce()).updateFirst(any(Query.class), captor.capture(), eq(BackgroundJob.class));
		var sets = captor.getAllValues().stream()
			.map(u -> (Document) u.getUpdateObject().get("$set"))
			.filter(d -> d != null && d.containsKey("finishedAt"))
			.toList();
		assertThat(sets).isNotEmpty();
		return sets.get(sets.size() - 1);
	}

	@Test
	void importsStagedFilesThroughTheLivePipeline() throws Exception {
		var job = bulkJob();
		givenClaimedJob(job);
		when(usageMonitoringService.hasExceededLimit(TENANT, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS))
			.thenReturn(false);
		when(s3StorageService.fetchObjectBytes(anyString())).thenReturn(new byte[] {1});

		worker.poll();

		verify(cvService).processFile(any(byte[].class), eq("a.pdf"), eq("application/pdf"), eq(TENANT));
		verify(cvService).processFile(any(byte[].class), eq("b.pdf"), eq("application/pdf"), eq(TENANT));
		verify(s3StorageService).deleteObject("s/0");
		verify(s3StorageService).deleteObject("s/1");
		verify(jobPostService).markOpenJobPostsAsNeedingReports(TENANT);
		verify(cacheEvictor).evict(TENANT);

		var finalSet = lastFinalUpdate();
		assertThat(finalSet.getString("status")).isEqualTo(BackgroundJob.STATUS_COMPLETED);
		assertThat(finalSet.getLong("succeeded")).isEqualTo(2L);
		assertThat(finalSet.getLong("failed")).isEqualTo(0L);
	}

	@Test
	void skipsRemainderWhenQuotaExhausted() throws Exception {
		var job = bulkJob();
		givenClaimedJob(job);
		when(usageMonitoringService.hasExceededLimit(TENANT, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS))
			.thenReturn(true);

		worker.poll();

		verify(cvService, never()).processFile(any(byte[].class), anyString(), anyString(), anyString());
		// Staged objects are cleaned up even though nothing was processed.
		verify(s3StorageService, times(2)).deleteObject(anyString());
		verify(jobPostService, never()).markOpenJobPostsAsNeedingReports(anyString());

		var finalSet = lastFinalUpdate();
		assertThat(finalSet.getString("status")).isEqualTo(BackgroundJob.STATUS_COMPLETED_WITH_ERRORS);
		assertThat(finalSet.getString("failureReason")).isEqualTo("quota_exceeded");
		assertThat(finalSet.getLong("skipped")).isEqualTo(2L);
	}

	@Test
	void aFailingFileIsCountedNotFatal() throws Exception {
		var job = bulkJob();
		givenClaimedJob(job);
		when(usageMonitoringService.hasExceededLimit(TENANT, UsageMonitoringService.FeatureKey.SCREENING_ACTIONS))
			.thenReturn(false);
		when(s3StorageService.fetchObjectBytes(anyString())).thenReturn(new byte[] {1});
		when(cvService.processFile(any(byte[].class), eq("a.pdf"), anyString(), anyString()))
			.thenThrow(new RuntimeException("unparseable"));
		when(cvService.processFile(any(byte[].class), eq("b.pdf"), anyString(), anyString()))
			.thenReturn(null);

		worker.poll();

		var finalSet = lastFinalUpdate();
		assertThat(finalSet.getString("status")).isEqualTo(BackgroundJob.STATUS_COMPLETED_WITH_ERRORS);
		assertThat(finalSet.getLong("succeeded")).isEqualTo(1L);
		assertThat(finalSet.getLong("failed")).isEqualTo(1L);
		@SuppressWarnings("unchecked")
		var samples = (List<String>) finalSet.get("errorSamples");
		assertThat(samples).anySatisfy(s -> assertThat(s).contains("a.pdf"));
	}
}
