package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dao.repository.CVInsightRepository;
import ai.qorva.core.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGapAnalysisHandler implements InsightHandler {

	private final CVInsightRepository cvInsightRepository;

	@Override
	public InsightHandlerResult handle(ExtractedFilters filters, ObjectId tenantId) {
		List<SkillFrequencyResult> skillReport = cvInsightRepository.getSkillFrequencyReport(tenantId, filters, 50);

		List<String> rareSkills = skillReport.stream()
			.filter(sr -> sr.count() <= 2)
			.map(SkillFrequencyResult::skill)
			.collect(Collectors.toList());

		List<String> missingSkills = List.of();
		if (filters.skills() != null && !filters.skills().isEmpty()) {
			Set<String> presentSkills = skillReport.stream()
				.map(sr -> sr.skill().toLowerCase())
				.collect(Collectors.toSet());
			missingSkills = filters.skills().stream()
				.filter(requested -> !presentSkills.contains(requested.toLowerCase()))
				.collect(Collectors.toList());
		}

		List<InsightMetricDTO> metrics = List.of(
			new InsightMetricDTO("Rare Skills (≤2 candidates)", String.valueOf(rareSkills.size()), "skills"),
			new InsightMetricDTO("Missing Requested Skills", String.valueOf(missingSkills.size()), "skills")
		);

		List<String> chartLabels = new ArrayList<>();
		List<Number> chartValues = new ArrayList<>();
		for (SkillFrequencyResult sr : skillReport) {
			chartLabels.add(sr.skill());
			chartValues.add(sr.count());
		}
		List<ChartDataDTO> charts = chartLabels.isEmpty() ? List.of() :
			List.of(new ChartDataDTO("bar", "Skill Frequency Distribution", chartLabels, chartValues));

		Map<String, Object> rawData = Map.of(
			"rareSkills", rareSkills,
			"missingSkills", missingSkills
		);

		return new InsightHandlerResult(List.of(), skillReport.size(), metrics, charts, rawData);
	}
}
