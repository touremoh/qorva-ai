package ai.qorva.core.service;

import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.service.orchestrators.handlers.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class InsightRouter {

	private final Map<InsightIntent, InsightHandler> handlerMap;
	private final GeneralRecruitingChatHandler fallback;

	public InsightRouter(
		TalentPoolIntelligenceHandler talentPool,
		SkillGapAnalysisHandler skillGap,
		CandidateRankingHandler ranking,
		CandidateRediscoveryHandler rediscovery,
		TalentClusteringHandler clustering,
		GeneralRecruitingChatHandler general
	) {
		this.fallback = general;
		this.handlerMap = Map.ofEntries(
			Map.entry(InsightIntent.TALENT_POOL_INTELLIGENCE, talentPool),
			Map.entry(InsightIntent.SKILL_GAP_ANALYSIS, skillGap),
			Map.entry(InsightIntent.CANDIDATE_RANKING, ranking),
			Map.entry(InsightIntent.CANDIDATE_REDISCOVERY, rediscovery),
			Map.entry(InsightIntent.TALENT_CLUSTERING, clustering),
			Map.entry(InsightIntent.GENERAL_RECRUITING_QUESTION, general),
			// Phase 2 intents — mapped to closest Phase 1 handler until fully implemented
			Map.entry(InsightIntent.LOCATION_INTELLIGENCE, talentPool),           // pool analysis scoped to a location
			Map.entry(InsightIntent.SALARY_EXPECTATION_ANALYSIS, talentPool),     // pool-level aggregate metric
			Map.entry(InsightIntent.CANDIDATE_COMPARISON, ranking),               // comparing = ranking with narrow filter
			Map.entry(InsightIntent.JOB_DESCRIPTION_ANALYSIS, general),           // no DB query needed
			Map.entry(InsightIntent.RESUME_DATA_QUALITY_ANALYSIS, talentPool),    // pool-level data quality metric
			Map.entry(InsightIntent.SENIORITY_DISTRIBUTION_ANALYSIS, clustering)  // distribution = clustering by seniority
		);
	}

	public InsightHandler route(InsightIntent intent) {
		InsightHandler handler = handlerMap.get(intent);
		if (handler == null) {
			log.warn("No handler registered for intent {}; routing to general fallback", intent);
			return fallback;
		}
		if (isPhase2Intent(intent)) {
			log.info("Phase 2 intent {} routed to Phase 1 handler {} as approximation", intent, handler.getClass().getSimpleName());
		}
		return handler;
	}

	private boolean isPhase2Intent(InsightIntent intent) {
		return switch (intent) {
			case LOCATION_INTELLIGENCE, SALARY_EXPECTATION_ANALYSIS, CANDIDATE_COMPARISON,
				JOB_DESCRIPTION_ANALYSIS, RESUME_DATA_QUALITY_ANALYSIS, SENIORITY_DISTRIBUTION_ANALYSIS -> true;
			default -> false;
		};
	}
}
