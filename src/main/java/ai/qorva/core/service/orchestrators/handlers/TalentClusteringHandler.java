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
public class TalentClusteringHandler implements InsightHandler {

	private static final List<String> CLUSTER_DIMENSIONS = List.of(
		"seniorityLevel", "skillDepth", "leadershipAndInfluence", "learningVelocity"
	);

	private static final Map<String, String> DIMENSION_CHART_TITLE_KEYS = Map.of(
		"seniorityLevel",         "CHART_TITLE_SENIORITY_LEVEL_DISTRIBUTION",
		"skillDepth",             "CHART_TITLE_SKILL_DEPTH_DISTRIBUTION",
		"leadershipAndInfluence", "CHART_TITLE_LEADERSHIP_INFLUENCE_DISTRIBUTION",
		"learningVelocity",       "CHART_TITLE_LEARNING_VELOCITY_DISTRIBUTION"
	);

	private static final Map<String, String> DIMENSION_LABEL_KEYS = Map.of(
		"seniorityLevel",         "DIMENSION_SENIORITY_LEVEL",
		"skillDepth",             "DIMENSION_SKILL_DEPTH",
		"leadershipAndInfluence", "DIMENSION_LEADERSHIP_AND_INFLUENCE",
		"learningVelocity",       "DIMENSION_LEARNING_VELOCITY"
	);

	private static final Map<String, String> CLUSTER_VALUE_KEYS = Map.ofEntries(
		Map.entry("junior",                "SENIORITY_JUNIOR"),
		Map.entry("midLevel",              "SENIORITY_MID_LEVEL"),
		Map.entry("senior",                "SENIORITY_SENIOR"),
		Map.entry("lead",                  "SENIORITY_LEAD"),
		Map.entry("principal",             "SENIORITY_PRINCIPAL"),
		Map.entry("manager",               "SENIORITY_MANAGER"),
		Map.entry("director",              "SENIORITY_DIRECTOR"),
		Map.entry("executive",             "SENIORITY_EXECUTIVE"),
		Map.entry("generalist",            "SKILL_DEPTH_GENERALIST"),
		Map.entry("specialist",            "SKILL_DEPTH_SPECIALIST"),
		Map.entry("tShaped",               "SKILL_DEPTH_T_SHAPED"),
		Map.entry("hybrid",                "SKILL_DEPTH_HYBRID"),
		Map.entry("none",                  "LEADERSHIP_NONE"),
		Map.entry("individualContributor", "LEADERSHIP_INDIVIDUAL_CONTRIBUTOR"),
		Map.entry("teamLead",              "LEADERSHIP_TEAM_LEAD"),
		Map.entry("crossFunctionalLeader", "LEADERSHIP_CROSS_FUNCTIONAL_LEADER"),
		Map.entry("strategicLeader",       "LEADERSHIP_STRATEGIC_LEADER"),
		Map.entry("executiveInfluence",    "LEADERSHIP_EXECUTIVE_INFLUENCE"),
		Map.entry("low",                   "LEARNING_VELOCITY_LOW"),
		Map.entry("medium",                "LEARNING_VELOCITY_MEDIUM"),
		Map.entry("high",                  "LEARNING_VELOCITY_HIGH"),
		Map.entry("veryHigh",              "LEARNING_VELOCITY_VERY_HIGH"),
		Map.entry("unknown",               "CLUSTER_UNKNOWN")
	);

	private final CVInsightRepository cvInsightRepository;

	@Override
	public InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId) {
		List<ChartDataDTO> charts = new ArrayList<>();
		List<InsightMetricDTO> metrics = new ArrayList<>();
		Map<String, Object> rawData = new HashMap<>();

		for (String dimension : CLUSTER_DIMENSIONS) {
			List<ClusterBucket> buckets = cvInsightRepository.getClusterDistributionReport(tenantId, params, dimension);
			if (buckets.isEmpty()) {
				continue;
			}

			long total = buckets.stream().mapToLong(ClusterBucket::count).sum();
			List<String> labels = buckets.stream()
				.map(b -> CLUSTER_VALUE_KEYS.getOrDefault(b.name(), b.name()))
				.collect(Collectors.toList());
			List<Number> values = buckets.stream()
				.map(b -> (Number) Math.round(b.count() * 100.0 / total))
				.collect(Collectors.toList());
			charts.add(new ChartDataDTO("pie", DIMENSION_CHART_TITLE_KEYS.get(dimension), labels, values));

			String dimensionLabelKey = DIMENSION_LABEL_KEYS.get(dimension);
			metrics.add(new InsightMetricDTO(dimensionLabelKey, "TOTAL_CANDIDATES", String.valueOf(total), "UNIT_CANDIDATES", "100"));
			for (ClusterBucket bucket : buckets) {
				long percent = Math.round(bucket.count() * 100.0 / total);
				metrics.add(new InsightMetricDTO(
					dimensionLabelKey,
					CLUSTER_VALUE_KEYS.getOrDefault(bucket.name(), bucket.name()),
					String.valueOf(bucket.count()),
					"UNIT_CANDIDATES",
					String.valueOf(percent)
				));
			}

			rawData.put(dimension, buckets);
		}

		return new InsightHandlerResult(List.of(), 0, metrics, charts, rawData);
	}
}
