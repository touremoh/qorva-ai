package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dao.repository.CVInsightRepository;
import ai.qorva.core.dto.*;
import ai.qorva.core.service.ResumeVectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateRankingHandler implements InsightHandler {

	private final ResumeVectorSearchService vectorSearchService;
	private final CVInsightRepository cvInsightRepository;

	private static final int DEFAULT_LIMIT = 10;

	@Override
	public InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId) {
		long poolCount = cvInsightRepository.countCandidatesByFilters(tenantId, params);

		if (poolCount == 0) {
			return new InsightHandlerResult(List.of(), 0L,
				List.of(new InsightMetricDTO("TOTAL_MATCHING_PROFILES", "0", "UNIT_PROFILES")),
				List.of(), Map.of());
		}

		int requestedLimit = params.limit() != null ? params.limit() : DEFAULT_LIMIT;
		int effectiveLimit = (int) Math.min(requestedLimit, poolCount);

		CVQueryParams adjustedParams = new CVQueryParams(
			params.skills(), params.roles(), params.industries(), params.languages(),
			params.companies(), params.degreeLevels(), params.institutions(),
			params.seniority(), params.skillDepth(), params.leadershipLevel(),
			params.openToWork(), params.availabilityStatus(), params.location(),
			params.minYearsExperience(), params.tags(), effectiveLimit,
			params.requiredSkills(), params.requiredIndustries(), null,
			params.applicantNumbers(), params.jobPostReference()
		);

		var cvs = vectorSearchService.search(adjustedParams, tenantId);

		List<CandidateCardDTO> candidates = cvs.stream()
			.sorted(Comparator.comparingDouble(cv -> -(cv.getScore() != null ? cv.getScore() : 0.0)))
			.map(CandidateCardMapper::toCard)
			.collect(Collectors.toList());

		List<InsightMetricDTO> metrics = List.of(
			new InsightMetricDTO("TOTAL_MATCHING_PROFILES", String.valueOf(poolCount), "UNIT_PROFILES"),
			new InsightMetricDTO("SHOWN_IN_RANKING", String.valueOf(candidates.size()), "UNIT_CANDIDATES")
		);

		return new InsightHandlerResult(candidates, poolCount, metrics, List.of(), Map.of());
	}
}
