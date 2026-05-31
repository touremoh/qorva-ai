package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.TextSearchRepository;
import ai.qorva.core.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TalentPoolIntelligenceHandler implements InsightHandler {

	private static final int TOP_SKILLS_LIMIT = 10;
	private static final int TEXT_SEARCH_LIMIT = 500;

	private final TextSearchRepository textSearchRepository;

	@Override
	public InsightHandlerResult handle(ExtractedFilters filters, ObjectId tenantId) {
		List<String> textTerms = buildTextTerms(filters);
		List<String> industryTerms = filters != null && filters.industries() != null ? filters.industries() : List.of();

		if (textTerms.isEmpty()) {
			log.warn("TalentPoolIntelligenceHandler – no search terms extracted from filters, returning empty result");
			return new InsightHandlerResult(List.of(), 0L,
				List.of(new InsightMetricDTO("Total Matching Candidates", "0", "candidates")),
				List.of(), Map.of());
		}

		long count = textSearchRepository.textSearchCount(textTerms, industryTerms, tenantId);
		List<CV> cvs = textSearchRepository.textSearch(textTerms, industryTerms, tenantId, TEXT_SEARCH_LIMIT);

		List<InsightMetricDTO> metrics = List.of(
			new InsightMetricDTO("Total Matching Candidates", String.valueOf(count), "candidates")
		);

		List<ChartDataDTO> charts = buildSkillChart(cvs);

		return new InsightHandlerResult(List.of(), count, metrics, charts, Map.of());
	}

	// Roles + skills go into $text (OR within each group) — industries are handled separately as AND filter
	private List<String> buildTextTerms(ExtractedFilters filters) {
		if (filters == null) {
			return List.of();
		}
		List<String> terms = new ArrayList<>();
		Stream.of(
			filters.roles(),
			filters.skills(),
			filters.tags(),
			filters.seniority() != null ? List.of(filters.seniority()) : List.<String>of(),
			filters.leadershipLevel() != null ? List.of(filters.leadershipLevel()) : List.<String>of(),
			filters.location() != null ? List.of(filters.location()) : List.<String>of()
		).filter(l -> l != null && !l.isEmpty()).flatMap(Collection::stream).forEach(terms::add);
		return terms;
	}

	private List<ChartDataDTO> buildSkillChart(List<CV> cvs) {
		Map<String, Long> skillFreq = cvs.stream()
			.filter(cv -> cv.getKeySkills() != null)
			.flatMap(cv -> cv.getKeySkills().stream())
			.filter(ks -> ks.getSkills() != null)
			.flatMap(ks -> ks.getSkills().stream())
			.filter(s -> s != null && !s.isBlank())
			.collect(Collectors.groupingBy(s -> s, Collectors.counting()));

		List<Map.Entry<String, Long>> top = skillFreq.entrySet().stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
			.limit(TOP_SKILLS_LIMIT)
			.collect(Collectors.toList());

		if (top.isEmpty()) {
			return List.of();
		}

		List<String> labels = top.stream().map(Map.Entry::getKey).collect(Collectors.toList());
		List<Number> values = top.stream().map(Map.Entry::getValue).collect(Collectors.toList());
		return List.of(new ChartDataDTO("bar", "Top Skills in Pool", labels, values));
	}
}
