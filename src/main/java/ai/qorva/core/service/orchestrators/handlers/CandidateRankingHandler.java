package ai.qorva.core.service.orchestrators.handlers;

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

	@Override
	public InsightHandlerResult handle(ExtractedFilters filters, ObjectId tenantId) {
		var cvs = vectorSearchService.search(filters, tenantId);

		List<CandidateCardDTO> candidates = cvs.stream()
			.sorted(Comparator.comparingDouble(cv -> -(cv.getScore() != null ? cv.getScore() : 0.0)))
			.map(CandidateCardMapper::toCard)
			.collect(Collectors.toList());

		List<InsightMetricDTO> metrics = List.of(
			new InsightMetricDTO("Top Candidates Found", String.valueOf(candidates.size()), "profiles")
		);

		return new InsightHandlerResult(candidates, candidates.size(), metrics, List.of(), Map.of());
	}
}
