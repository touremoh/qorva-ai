package ai.qorva.core.service.ats;

import ai.qorva.core.config.AtsProperties;
import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.dao.repository.BackgroundJobRepository;
import ai.qorva.core.dto.common.AtsRef;
import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.LibraryQualityCacheEvictor;
import ai.qorva.core.service.ScoringRulesPrefillService;
import ai.qorva.core.service.UsageMonitoringService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtsSyncServiceJobPostTest {

	@Mock private AtsConnectionRepository connectionRepository;
	@Mock private BackgroundJobRepository jobRepository;
	@Mock private AtsConnectionService connectionService;
	@Mock private AtsConnectorRegistry registry;
	@Mock private AtsOauthService oauthService;
	@Mock private CVService cvService;
	@Mock private JobPostService jobPostService;
	@Mock private UsageMonitoringService usageMonitoringService;
	@Mock private LibraryQualityCacheEvictor cacheEvictor;
	@Mock private ScoringRulesPrefillService scoringRulesPrefillService;
	@Mock private AtsProperties properties;
	@Mock private MongoTemplate mongoTemplate;

	private AtsSyncService service;

	@BeforeEach
	void setUp() {
		service = new AtsSyncService(connectionRepository, jobRepository, connectionService, registry,
			oauthService, cvService, jobPostService, usageMonitoringService, cacheEvictor,
			scoringRulesPrefillService, properties, mongoTemplate);
	}

	private static AtsConnection connection() {
		var connection = new AtsConnection();
		connection.setId("6aa4696283dcb9db7e98da3c");
		connection.setTenantId("6a654169f1cbc9a5ae47405b");
		connection.setProvider("recruitee");
		return connection;
	}

	private static AtsModels.AtsJob job(String description) {
		return new AtsModels.AtsJob("2744061", "Senior Marketer", description, true,
			Instant.parse("2026-09-11T20:54:30Z"), "https://acme.recruitee.com/o/senior-marketer");
	}

	private JobPost captureInserted() {
		var captor = ArgumentCaptor.forClass(JobPost.class);
		verify(mongoTemplate).insert(captor.capture());
		return captor.getValue();
	}

	/** The $set document of the single update this upsert issued. */
	private Document captureSet() {
		var captor = ArgumentCaptor.forClass(Update.class);
		verify(mongoTemplate).updateFirst(any(Query.class), captor.capture(), eq(JobPost.class));
		return captor.getValue().getUpdateObject().get("$set", Document.class);
	}

	/**
	 * An imported job never passes through the creation wizard that drafts criteria, so the
	 * import has to do it — otherwise the job is screened against nothing at all.
	 */
	@Test
	void aNewImportedJobGetsCriteriaDraftedFromItsDescription() throws Exception {
		var drafted = new ScoringRules();
		when(scoringRulesPrefillService.suggestUnmetered("Senior Marketer", "<p>Own the funnel.</p>"))
			.thenReturn(drafted);

		service.upsertJobPost(connection(), job("<p>Own the funnel.</p>"));

		assertThat(captureInserted().getScoringRules()).isSameAs(drafted);
	}

	/** The metered call bills a screening action; an import nobody asked for must not. */
	@Test
	void theImportNeverBillsAScreeningActionForTheSuggestion() throws Exception {
		when(scoringRulesPrefillService.suggestUnmetered(anyString(), anyString()))
			.thenReturn(new ScoringRules());

		service.upsertJobPost(connection(), job("<p>Own the funnel.</p>"));

		verify(scoringRulesPrefillService, never()).suggest(anyString(), anyString(), anyString());
		verifyNoInteractions(usageMonitoringService);
	}

	/** A description-less job would only make the prefill throw, so it is not even attempted. */
	@Test
	void aJobWithoutADescriptionIsImportedWithoutCallingTheModel() {
		service.upsertJobPost(connection(), job(null));

		assertThat(captureInserted().getScoringRules()).isNull();
		verifyNoInteractions(scoringRulesPrefillService);
	}

	/** Criteria a recruiter has since tuned by hand must survive every later sync. */
	@Test
	void existingCriteriaAreNeverOverwritten() {
		var existing = new JobPost();
		existing.setScoringRules(new ScoringRules());
		when(mongoTemplate.findOne(any(Query.class), eq(JobPost.class))).thenReturn(existing);

		service.upsertJobPost(connection(), job("<p>Own the funnel.</p>"));

		assertThat(captureSet()).doesNotContainKey("scoringRules");
		verifyNoInteractions(scoringRulesPrefillService);
	}

	/** Jobs imported before descriptions arrived have no criteria — the next sync fills them in. */
	@Test
	void aJobImportedWithoutCriteriaIsBackfilled() throws Exception {
		when(mongoTemplate.findOne(any(Query.class), eq(JobPost.class))).thenReturn(new JobPost());
		var drafted = new ScoringRules();
		when(scoringRulesPrefillService.suggestUnmetered(anyString(), anyString())).thenReturn(drafted);

		service.upsertJobPost(connection(), job("<p>Own the funnel.</p>"));

		assertThat(captureSet()).containsEntry("scoringRules", drafted);
	}

	/** The model being unavailable must cost the criteria, not the whole job. */
	@Test
	void aFailedDraftStillImportsTheJob() throws Exception {
		when(scoringRulesPrefillService.suggestUnmetered(anyString(), anyString()))
			.thenThrow(new QorvaException("boom"));

		service.upsertJobPost(connection(), job("<p>Own the funnel.</p>"));

		var inserted = captureInserted();
		assertThat(inserted.getScoringRules()).isNull();
		assertThat(inserted.getTitle()).isEqualTo("Senior Marketer");
	}

	/**
	 * An edit through the API used to drop atsRef, leaving a row that still owned the unique
	 * jobReference but no longer matched the link lookup. The insert that followed died on
	 * job_posts_job_reference_idx and took the whole sync run down with it. Adopt the row.
	 */
	@Test
	void aJobThatLostItsAtsRefIsReLinkedRatherThanInsertedAgain() {
		var orphan = new JobPost();
		orphan.setId("6aa46a8683dcb9db7e98da40");
		orphan.setScoringRules(new ScoringRules());
		when(mongoTemplate.findOne(any(Query.class), eq(JobPost.class)))
			.thenReturn(null)      // nothing carries this atsRef any more
			.thenReturn(orphan);   // but a row still holds ATS-2744061

		service.upsertJobPost(connection(), job("<p>Own the funnel.</p>"));

		verify(mongoTemplate, never()).insert(any(JobPost.class));
		var relinked = (AtsRef) captureSet().get("atsRef");
		assertThat(relinked.getExternalId()).isEqualTo("2744061");
		assertThat(relinked.getProvider()).isEqualTo("recruitee");
	}

	/** The link is rewritten every run, so lastImportedAt cannot go stale on an unchanged job. */
	@Test
	void anAlreadyLinkedJobHasItsLinkRefreshed() {
		var existing = new JobPost();
		existing.setScoringRules(new ScoringRules());
		when(mongoTemplate.findOne(any(Query.class), eq(JobPost.class))).thenReturn(existing);

		service.upsertJobPost(connection(), job("<p>Own the funnel.</p>"));

		assertThat(captureSet()).containsKey("atsRef");
	}
}
