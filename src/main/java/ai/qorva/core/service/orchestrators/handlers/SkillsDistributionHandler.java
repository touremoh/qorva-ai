package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dao.repository.CVInsightRepository;
import ai.qorva.core.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillsDistributionHandler implements InsightHandler {

	private static final int DEFAULT_LIMIT = 15;

	private final CVInsightRepository cvInsightRepository;

	@Override
	public InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId) {
		int limit = params.limit() != null ? params.limit() : DEFAULT_LIMIT;
		List<SkillFrequencyResult> skills = cvInsightRepository.getSkillIndexFrequencyReport(tenantId, params, limit);

		long totalCandidates = cvInsightRepository.countCandidatesByFilters(tenantId, params);

		if (skills.isEmpty() || totalCandidates == 0) {
			return InsightHandlerResult.empty();
		}

		List<String> labels = skills.stream().map(SkillFrequencyResult::skill).toList();
		List<Number> values = skills.stream().map(s -> (Number) s.count()).toList();
		ChartDataDTO chart = new ChartDataDTO("bar", "CHART_TITLE_SKILLS_DISTRIBUTION", labels, values);

		List<InsightMetricDTO> metrics = new ArrayList<>();
		metrics.add(new InsightMetricDTO("DIMENSION_SKILLS", "TOTAL_CANDIDATES", String.valueOf(totalCandidates), "UNIT_CANDIDATES", "100"));
		for (SkillFrequencyResult s : skills) {
			long pct = Math.round(s.count() * 100.0 / totalCandidates);
			metrics.add(new InsightMetricDTO("DIMENSION_SKILLS", s.skill(), String.valueOf(s.count()), "UNIT_CANDIDATES", String.valueOf(pct)));
		}

		return new InsightHandlerResult(List.of(), totalCandidates, metrics, List.of(chart), Map.of("skills", skills));
	}
}
