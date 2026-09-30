package ai.qorva.core.service;

import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dto.InsightTexts;
import ai.qorva.core.dto.UsageForecast;
import ai.qorva.core.dto.UsageInsight;
import ai.qorva.core.dto.UsageInsight.Recommendation;
import ai.qorva.core.dto.UsageMonitoringDTO;
import ai.qorva.core.dto.common.UsageFeatureMetrics;
import ai.qorva.core.enums.JobPostStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.orchestrators.InsightTranslationAgent;
import ai.qorva.core.service.orchestrators.UsageInsightAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The AI summary shown on the Usage Monitoring page: which allowance to watch and how to use less.
 * <p>
 * Usage moves with every upload, so the fingerprint is coarse on purpose — each meter's share in
 * 5 % steps, its pace status, and a days-left bucket. The advice stays true inside a step and is
 * rewritten as soon as the risk changes. The drivers (open jobs, resumes added) are only counted
 * when a summary is actually generated.
 */
@Slf4j
@Service
public class UsageInsightService {

	static final int MAX_RECOMMENDATIONS = 4;
	static final Set<String> METERS = Set.of("screeningActions", "aiResumeChats", "talentIntelligenceQueries", "agentRuns");

	private final UsageMonitoringService usageMonitoringService;
	private final TenantService tenantService;
	private final JobPostRepository jobPostRepository;
	private final CVRepository cvRepository;
	private final UsageInsightAgent agent;
	private final InsightTranslationAgent translationAgent;

	private final LocalizedInsightCache<UsageInsight> cache = new LocalizedInsightCache<>(Duration.ofHours(24), 10_000);

	public UsageInsightService(UsageMonitoringService usageMonitoringService, TenantService tenantService,
	                           JobPostRepository jobPostRepository, CVRepository cvRepository,
	                           UsageInsightAgent agent, InsightTranslationAgent translationAgent) {
		this.usageMonitoringService = usageMonitoringService;
		this.tenantService = tenantService;
		this.jobPostRepository = jobPostRepository;
		this.cvRepository = cvRepository;
		this.agent = agent;
		this.translationAgent = translationAgent;
	}

	/** {@code month} or {@code year}, from the tenant's subscription; null when unknown. */
	public String billingCycleOf(String tenantId) {
		try {
			var tenant = tenantService.findOneById(tenantId);
			return tenant != null && tenant.getSubscriptionInfo() != null ? tenant.getSubscriptionInfo().getBillingCycle() : null;
		} catch (QorvaException e) {
			return null;
		}
	}

	/** Empty when the tenant has no active usage period — there is nothing to explain. */
	public Optional<UsageInsight> getInsight(String tenantId, String acceptLanguage) throws QorvaException {
		var usage = usageMonitoringService.findCurrentPeriodByTenantId(tenantId).orElse(null);
		if (usage == null || usage.getFeatures() == null) {
			return Optional.empty();
		}
		var now = Instant.now();
		var billingCycle = billingCycleOf(tenantId);
		var forecast = UsageForecaster.forecast(usage, now);
		var fingerprint = fingerprint(usage, billingCycle, forecast, now);
		return Optional.of(cache.get(tenantId, fingerprint, LocalizedInsightCache.normalizeLanguage(acceptLanguage),
			() -> generate(tenantId, usage, billingCycle, forecast, now), this::translate));
	}

	private UsageInsight generate(String tenantId, UsageMonitoringDTO usage, String billingCycle,
	                              Map<String, UsageForecast> forecast, Instant now) throws QorvaException {
		long start = System.currentTimeMillis();
		var input = input(usage, billingCycle, forecast, drivers(tenantId, usage), now);
		var insight = sanitize(agent.generate(input));
		log.info("Usage insight generated ({} recommendations, {} ms)",
			insight.recommendations().size(), System.currentTimeMillis() - start);
		return insight;
	}

	private UsageInsight translate(UsageInsight canonical, String language) throws QorvaException {
		var texts = new InsightTexts(canonical.headline(), canonical.explanation(),
			canonical.recommendations().stream().map(Recommendation::text).toList());
		var translated = translationAgent.translate(texts, language, QorvaErrorCodes.USAGE_INSIGHT_UNAVAILABLE);
		var recommendations = new ArrayList<Recommendation>();
		for (int i = 0; i < canonical.recommendations().size(); i++) {
			recommendations.add(new Recommendation(translated.recommendations().get(i),
				canonical.recommendations().get(i).feature()));
		}
		return new UsageInsight(translated.headline(), translated.explanation(),
			List.copyOf(recommendations), language, canonical.generatedAt());
	}

	private UsageInsight.Drivers drivers(String tenantId, UsageMonitoringDTO usage) {
		var open = JobPostStatusEnum.OPEN.getStatus();
		return new UsageInsight.Drivers(
			jobPostRepository.countByTenantIdAndStatus(tenantId, open),
			jobPostRepository.countAwaitingMatching(tenantId, open),
			cvRepository.countCreatedSince(tenantId, usage.getCurrentPeriodStart()),
			CVService.DEFAULT_MATCH_LIMIT);
	}

	static UsageInsight.Input input(UsageMonitoringDTO usage, String billingCycle, Map<String, UsageForecast> forecast,
	                                UsageInsight.Drivers drivers, Instant now) {
		var meters = new LinkedHashMap<String, UsageInsight.Meter>();
		var features = usage.getFeatures();
		meter(meters, "screeningActions", features.getScreeningActions(), forecast);
		meter(meters, "aiResumeChats", features.getAiResumeChats(), forecast);
		meter(meters, "talentIntelligenceQueries", features.getTalentIntelligenceQueries(), forecast);
		meter(meters, "agentRuns", features.getAgentRuns(), forecast);
		return new UsageInsight.Input(
			usage.getSubscriptionTier(),
			billingCycle,
			isoDate(usage.getCurrentPeriodStart()),
			isoDate(usage.getCurrentPeriodEnd()),
			daysLeft(usage, now),
			meters,
			drivers);
	}

	private static void meter(Map<String, UsageInsight.Meter> meters, String key, UsageFeatureMetrics metrics,
	                          Map<String, UsageForecast> forecast) {
		if (metrics == null) return;
		var pace = forecast.get(key);
		int consumed = metrics.getConsumed() != null ? metrics.getConsumed() : 0;
		meters.put(key, new UsageInsight.Meter(
			metrics.getLimit(),
			consumed,
			percentUsed(metrics),
			pace != null ? pace.status() : null,
			pace != null ? pace.projectedConsumed() : null,
			pace != null ? isoDate(pace.limitReachedOn()) : null));
	}

	/** Keeps the model's output inside what the UI can act on: known meters, bounded length. */
	static UsageInsight sanitize(UsageInsight.Draft draft) {
		var recommendations = Optional.ofNullable(draft.recommendations()).orElse(List.of()).stream()
			.filter(r -> r != null && r.text() != null && !r.text().isBlank())
			.limit(MAX_RECOMMENDATIONS)
			.map(r -> new Recommendation(r.text().strip(), METERS.contains(r.feature()) ? r.feature() : null))
			.toList();
		return new UsageInsight(
			draft.headline().strip(),
			draft.explanation() == null ? "" : draft.explanation().strip(),
			recommendations,
			LocalizedInsightCache.DEFAULT_LANGUAGE,
			Instant.now());
	}

	/** Coarse on purpose: the same advice holds until a meter moves 5 %, changes pace, or the period nears its end. */
	static String fingerprint(UsageMonitoringDTO usage, String billingCycle, Map<String, UsageForecast> forecast, Instant now) {
		var features = usage.getFeatures();
		var meters = new LinkedHashMap<String, UsageFeatureMetrics>();
		meters.put("screeningActions", features.getScreeningActions());
		meters.put("aiResumeChats", features.getAiResumeChats());
		meters.put("talentIntelligenceQueries", features.getTalentIntelligenceQueries());
		meters.put("agentRuns", features.getAgentRuns());
		var perMeter = meters.entrySet().stream()
			.filter(e -> e.getValue() != null)
			.map(e -> {
				var percent = percentUsed(e.getValue());
				var pace = forecast.get(e.getKey());
				return e.getKey() + ":" + (percent == null ? "-" : (percent / 5) * 5) + ":" + (pace == null ? "-" : pace.status());
			})
			.collect(Collectors.joining(","));
		return String.join("|",
			String.valueOf(usage.getSubscriptionTier()),
			String.valueOf(billingCycle),
			String.valueOf(usage.getCurrentPeriodEnd()),
			perMeter,
			daysLeftBucket(daysLeft(usage, now)));
	}

	static Integer percentUsed(UsageFeatureMetrics metrics) {
		if (metrics.getLimit() == null || metrics.getLimit() <= 0) return null;
		int consumed = metrics.getConsumed() != null ? metrics.getConsumed() : 0;
		return (int) Math.min(100L, (long) consumed * 100 / metrics.getLimit());
	}

	static long daysLeft(UsageMonitoringDTO usage, Instant now) {
		long seconds = Duration.between(now, usage.getCurrentPeriodEnd()).getSeconds();
		return Math.max(0, (seconds + 86_399) / 86_400);
	}

	static String daysLeftBucket(long daysLeft) {
		if (daysLeft <= 2) return "0-2";
		if (daysLeft <= 7) return "3-7";
		if (daysLeft <= 14) return "8-14";
		return "15+";
	}

	private static String isoDate(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC).toLocalDate().toString();
	}
}
