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
	public InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId) {
		// Skills are the analysis subject, not a population gate — strip them from the
		// population criteria so the frequency report covers the full relevant pool.
		CVQueryParams populationParams = params.withoutSkills();

		long totalCandidates = cvInsightRepository.countCandidatesByFilters(tenantId, populationParams);
		List<String> requestedSkills = params.skills() != null ? params.skills() : List.of();

		if (requestedSkills.isEmpty()) {
			return handleDiscoveryMode(tenantId, populationParams, totalCandidates);
		}
		return handleCheckMode(tenantId, populationParams, requestedSkills, totalCandidates);
	}

	/**
	 * Discovery mode: no specific skill domain given (e.g. "what certifications are rare?").
	 * Fetches the actual low-frequency tail from the DB and lets the LLM interpret the results.
	 */
	private InsightHandlerResult handleDiscoveryMode(ObjectId tenantId, CVQueryParams populationParams, long totalCandidates) {
		int rareThreshold = (int) Math.max(3, totalCandidates * 0.10);
		List<SkillFrequencyResult> rareReport = cvInsightRepository.getRareSkillsReport(tenantId, populationParams, rareThreshold, 100);

		List<String> rareSkills = rareReport.stream().map(SkillFrequencyResult::skill).collect(Collectors.toList());

		List<ChartDataDTO> charts = rareReport.isEmpty() ? List.of() : List.of(
			new ChartDataDTO("bar", "CHART_TITLE_RARE_SKILLS_DISTRIBUTION",
				rareReport.stream().map(SkillFrequencyResult::skill).toList(),
				rareReport.stream().map(sr -> (Number) sr.count()).toList())
		);

		List<InsightMetricDTO> metrics = List.of(
			new InsightMetricDTO("TOTAL_CANDIDATES_ANALYZED", String.valueOf(totalCandidates), "UNIT_CANDIDATES"),
			new InsightMetricDTO("RARE_SKILLS_FOUND", String.valueOf(rareSkills.size()), "UNIT_SKILLS")
		);

		Map<String, Object> rawData = new LinkedHashMap<>();
		rawData.put("totalCandidatesInPool", totalCandidates);
		rawData.put("rareSkills", rareSkills);
		rawData.put("rareSkillDetails", rareReport.stream()
			.collect(Collectors.toMap(SkillFrequencyResult::skill, SkillFrequencyResult::count, (a, b) -> a, LinkedHashMap::new)));

		return new InsightHandlerResult(List.of(), totalCandidates, metrics, charts, rawData);
	}

	/**
	 * Check mode: a specific skill domain was given (e.g. "is cloud-native underrepresented?").
	 * Entity extractor has expanded the concept to concrete tokens — check each one's representation.
	 */
	private InsightHandlerResult handleCheckMode(ObjectId tenantId, CVQueryParams populationParams, List<String> requestedSkills, long totalCandidates) {
		List<SkillFrequencyResult> skillReport = cvInsightRepository.getSkillFrequencyReport(tenantId, populationParams, 50);

		Set<String> presentSkillsLower = skillReport.stream()
			.map(sr -> sr.skill().toLowerCase())
			.collect(Collectors.toSet());

		List<String> missingSkills = requestedSkills.stream()
			.filter(requested -> {
				String reqLower = requested.toLowerCase();
				return presentSkillsLower.stream()
					.noneMatch(present -> present.contains(reqLower) || reqLower.contains(present));
			})
			.collect(Collectors.toList());

		Map<String, Long> requestedSkillRepresentation = new LinkedHashMap<>();
		for (String requested : requestedSkills) {
			String reqLower = requested.toLowerCase();
			long count = skillReport.stream()
				.filter(sr -> {
					String skillLower = sr.skill().toLowerCase();
					return skillLower.contains(reqLower) || reqLower.contains(skillLower);
				})
				.mapToLong(SkillFrequencyResult::count)
				.max()
				.orElse(0L);
			requestedSkillRepresentation.put(requested, count);
		}

		int rareThreshold = (int) Math.max(3, totalCandidates * 0.10);
		List<String> rareSkills = requestedSkillRepresentation.entrySet().stream()
			.filter(e -> e.getValue() > 0 && e.getValue() <= rareThreshold)
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());

		List<ChartDataDTO> charts = skillReport.isEmpty() ? List.of() : List.of(
			new ChartDataDTO("bar", "CHART_TITLE_SKILL_FREQUENCY_DISTRIBUTION",
				skillReport.stream().map(SkillFrequencyResult::skill).toList(),
				skillReport.stream().map(sr -> (Number) sr.count()).toList())
		);

		List<InsightMetricDTO> metrics = List.of(
			new InsightMetricDTO("TOTAL_CANDIDATES_ANALYZED", String.valueOf(totalCandidates), "UNIT_CANDIDATES"),
			new InsightMetricDTO("RARE_SKILLS", String.valueOf(rareSkills.size()), "UNIT_SKILLS"),
			new InsightMetricDTO("MISSING_REQUESTED_SKILLS", String.valueOf(missingSkills.size()), "UNIT_SKILLS")
		);

		Map<String, Object> rawData = new LinkedHashMap<>();
		rawData.put("totalCandidatesInPool", totalCandidates);
		rawData.put("rareSkills", rareSkills);
		rawData.put("missingSkills", missingSkills);
		rawData.put("requestedSkillRepresentation", requestedSkillRepresentation);

		return new InsightHandlerResult(List.of(), totalCandidates, metrics, charts, rawData);
	}
}
