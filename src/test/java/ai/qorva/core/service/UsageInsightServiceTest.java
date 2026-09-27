package ai.qorva.core.service;

import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dto.InsightTexts;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UsageInsight;
import ai.qorva.core.dto.UsageInsight.DraftRecommendation;
import ai.qorva.core.dto.UsageMonitoringDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.dto.common.UsageFeatureMetrics;
import ai.qorva.core.dto.common.UsageFeatures;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.orchestrators.InsightTranslationAgent;
import ai.qorva.core.service.orchestrators.UsageInsightAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageInsightServiceTest {

	private static final String TENANT = "64b000000000000000000001";
	/** Fixed for the whole test: the period end is part of the fingerprint. */
	private static final Instant START = Instant.now().minus(5, ChronoUnit.DAYS);

	private UsageMonitoringService usageMonitoringService;
	private JobPostRepository jobPosts;
	private CVRepository cvs;
	private UsageInsightAgent agent;
	private InsightTranslationAgent translator;
	private UsageInsightService service;

	@BeforeEach
	void setUp() throws QorvaException {
		usageMonitoringService = mock(UsageMonitoringService.class);
		var tenantService = mock(TenantService.class);
		jobPosts = mock(JobPostRepository.class);
		cvs = mock(CVRepository.class);
		agent = mock(UsageInsightAgent.class);
		translator = mock(InsightTranslationAgent.class);
		service = new UsageInsightService(usageMonitoringService, tenantService, jobPosts, cvs, agent, translator);

		var tenant = new TenantDTO();
		var subscription = new SubscriptionInfo();
		subscription.setBillingCycle("month");
		tenant.setSubscriptionInfo(subscription);
		when(tenantService.findOneById(TENANT)).thenReturn(tenant);
		when(jobPosts.countByTenantIdAndStatus(eq(TENANT), anyString())).thenReturn(23L);
		when(jobPosts.countAwaitingMatching(eq(TENANT), anyString())).thenReturn(9L);
		when(cvs.countCreatedSince(eq(TENANT), any())).thenReturn(140L);
		when(agent.generate(any())).thenReturn(new UsageInsight.Draft("Watch matching actions", "Why", List.of(
			new DraftRecommendation("Close jobs you no longer hire for", "screeningActions"),
			new DraftRecommendation("Invented meter", "storage"))));
	}

	@Test
	void noActivePeriod_hasNoInsight() throws QorvaException {
		when(usageMonitoringService.findCurrentPeriodByTenantId(TENANT)).thenReturn(Optional.empty());

		assertThat(service.getInsight(TENANT, "en")).isEmpty();
		verify(agent, never()).generate(any());
	}

	@Test
	void smallMoves_reuseTheSummary_andDriversAreCountedOnlyOnGeneration() throws QorvaException {
		when(usageMonitoringService.findCurrentPeriodByTenantId(TENANT)).thenReturn(Optional.of(usage(3000)));
		service.getInsight(TENANT, "en");
		when(usageMonitoringService.findCurrentPeriodByTenantId(TENANT)).thenReturn(Optional.of(usage(3040)));
		service.getInsight(TENANT, "en");

		verify(agent, times(1)).generate(any());
		verify(jobPosts, times(1)).countByTenantIdAndStatus(eq(TENANT), anyString());
	}

	@Test
	void crossingAFivePercentStep_regenerates() throws QorvaException {
		when(usageMonitoringService.findCurrentPeriodByTenantId(TENANT)).thenReturn(Optional.of(usage(3000)));
		service.getInsight(TENANT, "en");
		when(usageMonitoringService.findCurrentPeriodByTenantId(TENANT)).thenReturn(Optional.of(usage(3600)));
		service.getInsight(TENANT, "en");

		verify(agent, times(2)).generate(any());
	}

	@Test
	void theModelSeesTheMetersPaceAndDrivers() throws QorvaException {
		when(usageMonitoringService.findCurrentPeriodByTenantId(TENANT)).thenReturn(Optional.of(usage(6000)));
		service.getInsight(TENANT, "en");

		var input = ArgumentCaptor.forClass(UsageInsight.Input.class);
		verify(agent).generate(input.capture());
		assertThat(input.getValue().tier()).isEqualTo("Pro");
		assertThat(input.getValue().billingCycle()).isEqualTo("month");
		assertThat(input.getValue().meters().get("screeningActions").percentUsed()).isEqualTo(60);
		assertThat(input.getValue().meters().get("screeningActions").pace()).isNotNull();
		assertThat(input.getValue().drivers()).isEqualTo(new UsageInsight.Drivers(23, 9, 140, CVService.DEFAULT_MATCH_LIMIT));
	}

	@Test
	void unknownMeters_areDropped_andTranslationsKeepTheMeterLinks() throws QorvaException {
		when(usageMonitoringService.findCurrentPeriodByTenantId(TENANT)).thenReturn(Optional.of(usage(3000)));
		when(translator.translate(any(), eq("fr"), any())).thenReturn(
			new InsightTexts("Surveillez", "Pourquoi", List.of("Fermez les offres", "Inventé")));

		var english = service.getInsight(TENANT, "en").orElseThrow();
		var french = service.getInsight(TENANT, "fr").orElseThrow();

		assertThat(english.recommendations()).extracting(UsageInsight.Recommendation::feature)
			.containsExactly("screeningActions", null);
		assertThat(french.language()).isEqualTo("fr");
		assertThat(french.recommendations()).extracting(UsageInsight.Recommendation::feature)
			.containsExactly("screeningActions", null);
		verify(agent, times(1)).generate(any());
	}

	@Test
	void daysLeftBuckets() {
		assertThat(UsageInsightService.daysLeftBucket(20)).isEqualTo("15+");
		assertThat(UsageInsightService.daysLeftBucket(10)).isEqualTo("8-14");
		assertThat(UsageInsightService.daysLeftBucket(5)).isEqualTo("3-7");
		assertThat(UsageInsightService.daysLeftBucket(1)).isEqualTo("0-2");
	}

	/** A 30-day Pro period that started five days ago. */
	private static UsageMonitoringDTO usage(int screeningConsumed) {
		var start = START;
		var usage = new UsageMonitoringDTO();
		usage.setTenantId(TENANT);
		usage.setSubscriptionTier("Pro");
		usage.setCurrentPeriodStart(start);
		usage.setCurrentPeriodEnd(start.plus(30, ChronoUnit.DAYS));
		usage.setFeatures(UsageFeatures.builder()
			.screeningActions(UsageFeatureMetrics.builder().limit(10000).consumed(screeningConsumed).cumulative(0L).build())
			.aiResumeChats(UsageFeatureMetrics.builder().limit(1500).consumed(10).cumulative(0L).build())
			.talentIntelligenceQueries(UsageFeatureMetrics.builder().limit(3000).consumed(0).cumulative(0L).build())
			.build());
		return usage;
	}
}
