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
			Map.entry(InsightIntent.GENERAL_RECRUITING_QUESTION, general)
		);
	}

	public InsightHandler route(InsightIntent intent) {
		InsightHandler handler = handlerMap.get(intent);
		if (handler == null) {
			log.warn("No handler registered for intent {}; routing to general fallback", intent);
			return fallback;
		}
		return handler;
	}
}
