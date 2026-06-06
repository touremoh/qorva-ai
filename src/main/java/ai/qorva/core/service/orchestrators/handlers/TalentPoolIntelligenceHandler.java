package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dao.repository.CVInsightRepository;
import ai.qorva.core.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TalentPoolIntelligenceHandler implements InsightHandler {

	private static final int TOP_SKILLS_LIMIT = 10;

	private final CVInsightRepository cvInsightRepository;

	@Override
	public InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId) {
		boolean hasTerms = (params.skills() != null && !params.skills().isEmpty())
			|| (params.roles() != null && !params.roles().isEmpty());

		if (!hasTerms) {
			log.warn("TalentPoolIntelligenceHandler – no skill or role terms in params, returning empty result");
			return new InsightHandlerResult(List.of(), 0L,
				List.of(new InsightMetricDTO("Total Matching Candidates", "0", "candidates")),
				List.of(), Map.of());
		}

		long count = cvInsightRepository.countCandidatesByFilters(tenantId, params);
		List<SkillFrequencyResult> topSkills = cvInsightRepository.getSkillFrequencyReport(tenantId, params, TOP_SKILLS_LIMIT);

		List<InsightMetricDTO> metrics = List.of(
			new InsightMetricDTO("Total Matching Candidates", String.valueOf(count), "candidates")
		);

		List<ChartDataDTO> charts = topSkills.isEmpty() ? List.of() : List.of(
			new ChartDataDTO(
				"bar",
				"Top Skills in Pool",
				topSkills.stream().map(SkillFrequencyResult::skill).toList(),
				topSkills.stream().map(sr -> (Number) sr.count()).toList()
			)
		);

		return new InsightHandlerResult(List.of(), count, metrics, charts, Map.of());
	}
}
